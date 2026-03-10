import java.util.List;
import java.util.concurrent.*;

/**
 * Orchestrates the full lifecycle of a Producer-Consumer transfer.
 *
 * <p>Workflow:
 * <ol>
 *   <li>startTransfer() submits both Producer and Consumer to a 2-thread pool.</li>
 *   <li>waitForCompletion() awaits the Producer future first. Once the producer
 *       finishes, the queue is closed so the consumer drains remaining items
 *       and exits via the null sentinel from dequeue().</li>
 *   <li>stopTransfer() provides an emergency halt path that cooperatively
 *       signals both threads and shuts down the executor immediately.</li>
 * </ol>
 *
 * <p>Producer is awaited before closing the queue deliberately: closing the
 * queue first would cause any in-flight enqueue() calls to throw
 * IllegalStateException, potentially losing items.
 */
public class DataTransferManager {
    private static final int  QUEUE_CAPACITY    = 10;
    private static final long DEFAULT_TIMEOUT_S = 60L;

    private final SharedQueue     sharedQueue;
    private final Producer        producer;
    private final Consumer        consumer;
    private final ExecutorService executor;
    private Future<?> producerFuture;
    private Future<?> consumerFuture;
    private volatile boolean transferStarted = false;

    public DataTransferManager(List<Item> sourceData) { this(sourceData, 80L, 150L); }

    /**
     * @param sourceData      items the producer will push onto the queue
     * @param produceDelayMs  simulated I/O delay between enqueues (ms)
     * @param consumeDelayMs  simulated processing delay between dequeues (ms)
     */
    public DataTransferManager(List<Item> sourceData, long produceDelayMs, long consumeDelayMs) {
        this.sharedQueue = new SharedQueue(QUEUE_CAPACITY);
        this.producer    = new Producer(sourceData, sharedQueue, produceDelayMs);
        this.consumer    = new Consumer(sharedQueue, consumeDelayMs);
        // Daemon threads: JVM won't hang if shutdown is forgotten
        this.executor    = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }

    /** Submits producer and consumer threads to the executor. */
    public void startTransfer() {
        if (transferStarted) throw new IllegalStateException("Transfer already started");
        transferStarted = true;
        producerFuture  = executor.submit(producer);
        consumerFuture  = executor.submit(consumer);
        System.out.println("[DataTransferManager] Transfer started.");
    }

    /**
     * Blocks until both threads complete or the timeout expires.
     *
     * @throws TimeoutException     if producer or consumer exceed the deadline
     * @throws ExecutionException   if either thread threw an unchecked exception
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public void waitForCompletion() throws InterruptedException, TimeoutException, ExecutionException {
        waitForCompletion(DEFAULT_TIMEOUT_S, TimeUnit.SECONDS);
    }

    public void waitForCompletion(long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException, ExecutionException {
        if (!transferStarted) throw new IllegalStateException("Call startTransfer() first");
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);

        // Step 1: wait for producer to push all source items
        try {
            producerFuture.get(deadlineNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            stopTransfer();
            throw new TimeoutException("Producer timed out");
        }

        // Step 2: close queue so consumer sees the null sentinel and drains remaining items
        sharedQueue.close();

        // Step 3: wait for consumer to fully drain
        try {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) throw new TimeoutException("No time left for consumer to drain");
            consumerFuture.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            stopTransfer();
            throw new TimeoutException("Consumer timed out draining");
        }

        executor.shutdown();
        System.out.println("[DataTransferManager] Transfer complete.");
    }

    /**
     * Emergency stop: signals both threads cooperatively, closes the queue,
     * and calls shutdownNow() on the executor. In-flight items may be lost.
     */
    public void stopTransfer() {
        System.out.println("[DataTransferManager] Emergency stop.");
        producer.stop();
        consumer.stop();
        sharedQueue.close();
        executor.shutdownNow();
    }

    /** Returns a thread-safe snapshot of the queue's current state. */
    public QueueStatus getQueueStatus()   { return sharedQueue.getQueueStatus(); }
    public int         getProducedCount() { return producer.getProducedCount(); }
    public int         getConsumedCount() { return consumer.getConsumedCount(); }
    public List<Item>  getConsumedItems() { return consumer.getDestinationContainer(); }
}

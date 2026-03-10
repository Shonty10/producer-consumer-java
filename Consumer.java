import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Continuously dequeues items from the SharedQueue and stores them
 * in an internal destinationContainer.
 *
 * <p>The consumer exits naturally when SharedQueue.dequeue() returns null,
 * which happens only when the queue is both closed AND empty. This guarantees
 * the consumer fully drains all remaining items before terminating — no data
 * is lost even if the producer finishes before the consumer catches up.
 *
 * <p>CopyOnWriteArrayList is used for destinationContainer so that the main
 * thread can safely read results while the consumer thread is still writing.
 */
public class Consumer implements Runnable {
    private final SharedQueue  sharedQueue;
    private final List<Item>   destinationContainer;
    private final long         consumeDelayMs;
    private final AtomicInteger consumedCount = new AtomicInteger(0);
    private volatile boolean    running       = true;

    public Consumer(SharedQueue sharedQueue) { this(sharedQueue, 150L); }

    /**
     * @param sharedQueue    source queue to consume from
     * @param consumeDelayMs artificial processing delay per item (ms)
     */
    public Consumer(SharedQueue sharedQueue, long consumeDelayMs) {
        if (sharedQueue == null) throw new IllegalArgumentException("sharedQueue is null");
        this.sharedQueue          = sharedQueue;
        this.consumeDelayMs       = consumeDelayMs;
        this.destinationContainer = new CopyOnWriteArrayList<>();
    }

    @Override
    public void run() {
        System.out.println("[Consumer] Thread started.");
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                Item item = sharedQueue.dequeue(); // blocks until item available or queue closed+empty
                if (item == null) break;           // null is the sentinel: no more items ever
                destinationContainer.add(item);
                consumedCount.incrementAndGet();
                System.out.printf("[Consumer] Processed: %-45s | total=%d%n", item, consumedCount.get());
                if (consumeDelayMs > 0) Thread.sleep(consumeDelayMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[Consumer] Interrupted.");
        } finally {
            System.out.println("[Consumer] Finished. Total consumed: " + consumedCount.get());
        }
    }

    /** Requests cooperative stop after the current item is processed. */
    public void stop()                          { running = false; }
    public int        getConsumedCount()        { return consumedCount.get(); }
    public List<Item> getDestinationContainer() { return destinationContainer; }
}

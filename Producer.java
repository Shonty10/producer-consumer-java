import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reads items from an in-memory sourceContainer and enqueues them
 * onto the SharedQueue one at a time.
 *
 * <p>The producer supports cooperative stopping via stop(): on the next
 * iteration it checks the running flag and exits cleanly without
 * interrupting the thread. This avoids the dangers of Thread.stop().
 *
 * <p>produceDelayMs simulates real-world I/O latency (e.g. reading from
 * a file or network) and creates natural back-pressure against the queue.
 */
public class Producer implements Runnable {
    private final List<Item>  sourceContainer;
    private final SharedQueue sharedQueue;
    private final long        produceDelayMs;
    private final AtomicInteger producedCount = new AtomicInteger(0);
    private volatile boolean    running       = true;

    public Producer(List<Item> sourceContainer, SharedQueue sharedQueue) {
        this(sourceContainer, sharedQueue, 80L);
    }

    /**
     * @param sourceContainer items to produce
     * @param sharedQueue     destination queue
     * @param produceDelayMs  artificial delay between enqueues (ms)
     */
    public Producer(List<Item> sourceContainer, SharedQueue sharedQueue, long produceDelayMs) {
        if (sourceContainer == null) throw new IllegalArgumentException("sourceContainer is null");
        if (sharedQueue     == null) throw new IllegalArgumentException("sharedQueue is null");
        this.sourceContainer = sourceContainer;
        this.sharedQueue     = sharedQueue;
        this.produceDelayMs  = produceDelayMs;
    }

    @Override
    public void run() {
        System.out.println("[Producer] Thread started. Items to produce: " + sourceContainer.size());
        try {
            for (Item item : sourceContainer) {
                if (!running || Thread.currentThread().isInterrupted()) {
                    System.out.println("[Producer] Stop signal received - halting early.");
                    break;
                }
                sharedQueue.enqueue(item);
                producedCount.incrementAndGet();
                if (produceDelayMs > 0) Thread.sleep(produceDelayMs);
            }
        } catch (InterruptedException e) {
            // Restore interrupt flag so callers can detect it
            Thread.currentThread().interrupt();
            System.out.println("[Producer] Interrupted.");
        } catch (IllegalStateException e) {
            System.out.println("[Producer] Queue closed prematurely: " + e.getMessage());
        } finally {
            System.out.println("[Producer] Finished. Total produced: " + producedCount.get());
        }
    }

    /** Requests cooperative stop. Producer finishes its current enqueue then exits. */
    public void stop()            { running = false; }
    public int getProducedCount() { return producedCount.get(); }
}

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe bounded queue for the Producer-Consumer pattern.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Uses a <b>fair</b> ReentrantLock (true) to prevent thread starvation
 *       when multiple producers or consumers compete.</li>
 *   <li>Two separate Condition objects (notFull / notEmpty) are used instead of
 *       one. With a single condition, signalling would wake both producers AND
 *       consumers unnecessarily. Separate conditions mean a producer only wakes
 *       consumers, and vice versa, reducing spurious context switching.</li>
 *   <li>signalAll() is used over signal() to avoid deadlock in scenarios with
 *       multiple waiting threads of the same type.</li>
 *   <li>close() broadcasts on both conditions so all blocked threads observe
 *       the terminal state and exit gracefully without being interrupted.</li>
 * </ul>
 */
public class SharedQueue {
    private final int capacity;
    private final Queue<Item> queue;
    private final ReentrantLock lock;
    private final Condition notFull;   // producers wait here when queue is full
    private final Condition notEmpty;  // consumers wait here when queue is empty
    private volatile boolean closed = false;

    public SharedQueue(int capacity) {
        if (capacity <= 0)
            throw new IllegalArgumentException("Capacity must be positive, got: " + capacity);
        this.capacity = capacity;
        this.queue    = new LinkedList<>();
        this.lock     = new ReentrantLock(true); // fair lock prevents starvation
        this.notFull  = lock.newCondition();
        this.notEmpty = lock.newCondition();
    }

    /**
     * Inserts an item into the queue, blocking if the queue is at capacity.
     * Wakes all waiting consumers after a successful insert.
     *
     * @throws InterruptedException  if the thread is interrupted while waiting
     * @throws IllegalStateException if the queue is closed before space is available
     * @throws IllegalArgumentException if item is null
     */
    public void enqueue(Item item) throws InterruptedException {
        if (item == null) throw new IllegalArgumentException("Cannot enqueue a null item");
        lock.lockInterruptibly();
        try {
            while (queue.size() >= capacity) {
                if (closed) throw new IllegalStateException("Cannot enqueue: queue is closed");
                System.out.printf("  [SharedQueue] FULL  (%2d/%d) - producer waiting...%n", queue.size(), capacity);
                notFull.await();
            }
            if (closed) throw new IllegalStateException("Cannot enqueue: queue is closed");
            queue.offer(item);
            System.out.printf("  [SharedQueue] ++ Enqueued   %-45s | size=%2d/%d%n", item, queue.size(), capacity);
            notEmpty.signalAll(); // wake waiting consumers
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the head of the queue, blocking if empty.
     * Returns null only when the queue is both closed AND empty,
     * signalling the consumer that no more items will ever arrive.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public Item dequeue() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty()) {
                if (closed) return null; // sentinel: producer is done, nothing more coming
                System.out.printf("  [SharedQueue] EMPTY         - consumer waiting...%n");
                notEmpty.await();
            }
            Item item = queue.poll();
            System.out.printf("  [SharedQueue] -- Dequeued   %-45s | size=%2d/%d%n", item, queue.size(), capacity);
            notFull.signalAll(); // wake waiting producers
            return item;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Like dequeue() but returns null after the timeout elapses with no item.
     * Useful for consumers that need a hard deadline.
     */
    public Item dequeueWithTimeout(long timeout, TimeUnit unit) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
            while (queue.isEmpty()) {
                if (closed) return null;
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) return null; // timeout expired
                notEmpty.awaitNanos(remaining);
            }
            Item item = queue.poll();
            notFull.signalAll();
            return item;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes the queue and broadcasts on both conditions so all blocked
     * threads (producers and consumers) wake up and observe the closed state.
     */
    public void close() {
        lock.lock();
        try {
            closed = true;
            notFull.signalAll();
            notEmpty.signalAll();
            System.out.println("  [SharedQueue] Queue closed.");
        } finally {
            lock.unlock();
        }
    }

    /** Returns a consistent point-in-time snapshot of queue state. */
    public QueueStatus getQueueStatus() {
        lock.lock();
        try { return new QueueStatus(queue.size(), capacity, closed); }
        finally { lock.unlock(); }
    }

    public boolean isEmpty()  { lock.lock(); try { return queue.isEmpty(); } finally { lock.unlock(); } }
    public boolean isClosed() { return closed; }
    public int     size()     { lock.lock(); try { return queue.size(); }    finally { lock.unlock(); } }
    public int     capacity() { return capacity; }
}

/**
 * Immutable snapshot of SharedQueue state at a point in time.
 *
 * <p>Returned by SharedQueue.getQueueStatus() to give callers a
 * consistent view of size, capacity, and closed state without
 * holding the queue's lock beyond the moment of capture.
 */
public final class QueueStatus {
    private final int currentSize;
    private final int capacity;
    private final double utilizationPercent;
    private final boolean closed;

    public QueueStatus(int currentSize, int capacity, boolean closed) {
        this.currentSize = currentSize;
        this.capacity = capacity;
        this.utilizationPercent = capacity > 0 ? (currentSize * 100.0) / capacity : 0.0;
        this.closed = closed;
    }

    public int getCurrentSize()           { return currentSize; }
    public int getCapacity()              { return capacity; }
    public double getUtilizationPercent() { return utilizationPercent; }
    public boolean isClosed()             { return closed; }
    public boolean isEmpty()              { return currentSize == 0; }
    public boolean isFull()               { return currentSize >= capacity; }

    @Override
    public String toString() {
        return String.format("QueueStatus{size=%d/%d, utilization=%.1f%%, closed=%b}",
                currentSize, capacity, utilizationPercent, closed);
    }
}

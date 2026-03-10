import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Immutable data object transferred between Producer and Consumer.
 *
 * <p>Immutability is intentional: once created, an Item's state cannot
 * change, making it inherently thread-safe to pass across thread boundaries
 * without additional synchronization.
 */
public final class Item {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final int id;
    private final String data;
    private final LocalDateTime timestamp;

    /**
     * @param id   unique identifier for this item
     * @param data payload string; must not be null or blank
     */
    public Item(int id, String data) {
        if (data == null || data.isBlank())
            throw new IllegalArgumentException("Item data cannot be null or blank");
        this.id = id;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    public int getId()                  { return id; }
    public String getData()             { return data; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("Item{id=%d, data='%s', ts=%s}", id, data, timestamp.format(FORMATTER));
    }
}

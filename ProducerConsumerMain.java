import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Entry point for the Producer-Consumer demonstration.
 *
 * <p>Compile and run:
 * <pre>
 *   javac -encoding UTF-8 *.java
 *   java ProducerConsumerMain
 * </pre>
 *
 * <p>Demo setup:
 * - 25 source items
 * - Producer delay: 80ms  (faster)
 * - Consumer delay: 150ms (slower) -> creates back-pressure, exercises queue-full wait path
 * - Queue capacity: 10 items
 * - A background reporter prints queue status every second
 */
public class ProducerConsumerMain {
    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("  Producer-Consumer Data Transfer Demo");
        System.out.println("=".repeat(70));

        // Build source data
        List<Item> sourceData = new ArrayList<>();
        for (int i = 1; i <= 25; i++)
            sourceData.add(new Item(i, "payload-" + String.format("%03d", i)));
        System.out.printf("%nSource container loaded: %d items%n%n", sourceData.size());

        DataTransferManager manager = new DataTransferManager(sourceData, 80L, 150L);

        // Background status reporter - prints queue utilization every second
        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "status-reporter");
            t.setDaemon(true);
            return t;
        });
        reporter.scheduleAtFixedRate(() ->
            System.out.printf("%n  >>> STATUS  produced=%-3d consumed=%-3d  %s%n%n",
                manager.getProducedCount(), manager.getConsumedCount(), manager.getQueueStatus()),
            1, 1, TimeUnit.SECONDS);

        long wallStart = System.currentTimeMillis();
        manager.startTransfer();

        try {
            manager.waitForCompletion(60, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.err.println("ERROR: " + e.getMessage());
            manager.stopTransfer();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            manager.stopTransfer();
        } catch (ExecutionException e) {
            System.err.println("ERROR: " + e.getCause());
            manager.stopTransfer();
        } finally {
            reporter.shutdownNow();
        }

        long elapsed = System.currentTimeMillis() - wallStart;
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  Final Transfer Summary");
        System.out.println("=".repeat(70));
        System.out.printf("  Items produced       : %d%n", manager.getProducedCount());
        System.out.printf("  Items consumed       : %d%n", manager.getConsumedCount());
        System.out.printf("  Final queue status   : %s%n", manager.getQueueStatus());
        System.out.printf("  Elapsed time         : %.2f s%n", elapsed / 1000.0);
        boolean ok = manager.getProducedCount() == sourceData.size()
                  && manager.getConsumedCount() == sourceData.size();
        System.out.println(ok ? "\n  [OK]  All items transferred successfully." : "\n  [FAIL]  Transfer incomplete.");
        System.out.println("=".repeat(70));
    }
}

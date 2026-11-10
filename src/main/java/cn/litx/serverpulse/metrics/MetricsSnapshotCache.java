package cn.litx.serverpulse.metrics;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Request-facing cache. The API only reads this cache; it never performs OSHI work.
 */
@Component
public class MetricsSnapshotCache {

    public static final int HISTORY_CAPACITY = 240;
    public static final int API_HISTORY_LIMIT = 120;
    public static final long STALE_AFTER_MS = 10_000L;

    private final AtomicReference<MetricsSnapshot> snapshot = new AtomicReference<>();
    private final HistoryBuffer history = new HistoryBuffer(HISTORY_CAPACITY);

    public void update(MetricsSnapshot value) {
        if (value == null) {
            return;
        }
        snapshot.set(value);
        var usage = value.usage();
        history.add(new HistoryPoint(
            value.generatedAt(),
            usage.cpuPercent(),
            usage.memoryPercent(),
            usage.storagePercent()
        ));
    }

    public Optional<MetricsSnapshot> current() {
        return Optional.ofNullable(snapshot.get());
    }

    public List<HistoryPoint> history() {
        return history.latest(API_HISTORY_LIMIT);
    }

    public boolean isStale(MetricsSnapshot value, long now) {
        return value == null || now - value.generatedAt() > STALE_AFTER_MS;
    }

    public void clear() {
        snapshot.set(null);
        history.clear();
    }
}

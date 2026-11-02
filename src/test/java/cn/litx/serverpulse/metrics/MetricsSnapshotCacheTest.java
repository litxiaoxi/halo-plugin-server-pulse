package cn.litx.serverpulse.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MetricsSnapshotCacheTest {

    @Test
    void storesSnapshotAndHistory() {
        var cache = new MetricsSnapshotCache();
        var usage = new Usage(10.0, 20.0, 30.0, 1, 2, 3, 4, 5);
        var snapshot = new MetricsSnapshot(
            1_000L,
            false,
            new StaticInfo(null, null, null, null, 0),
            usage,
            new RuntimeInfo(1, 2, 3)
        );

        cache.update(snapshot);

        assertTrue(cache.current().isPresent());
        assertEquals(1, cache.history().size());
        assertFalse(cache.isStale(snapshot, 1_500L));
        assertTrue(cache.isStale(snapshot, 20_000L));
    }

    @Test
    void historyIsLimitedForTheApi() {
        var cache = new MetricsSnapshotCache();
        for (var i = 0; i < 100; i++) {
            cache.update(new MetricsSnapshot(
                i,
                false,
                new StaticInfo(null, null, null, null, 0),
                new Usage(i, i, i, 0, 0, 0, 0, 0),
                new RuntimeInfo(0, 0, 0)
            ));
        }

        List<HistoryPoint> history = cache.history();
        assertEquals(MetricsSnapshotCache.API_HISTORY_LIMIT, history.size());
        assertEquals(40, history.getFirst().timestamp());
        assertEquals(99, history.getLast().timestamp());
    }
}

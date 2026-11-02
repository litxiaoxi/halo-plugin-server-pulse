package cn.litx.serverpulse.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HistoryBufferTest {

    @Test
    void keepsOnlyTheNewestPointsInOrder() {
        var buffer = new HistoryBuffer(3);
        for (var i = 0; i < 5; i++) {
            buffer.add(new HistoryPoint(i, i, i, i));
        }

        assertEquals(3, buffer.size());
        var latest = buffer.latest(10);
        assertEquals(3, latest.size());
        assertEquals(2, latest.get(0).timestamp());
        assertEquals(4, latest.get(2).timestamp());
    }

    @Test
    void latestHonoursTheLimit() {
        var buffer = new HistoryBuffer(10);
        for (var i = 0; i < 6; i++) {
            buffer.add(new HistoryPoint(i, 0, 0, 0));
        }

        var latest = buffer.latest(2);
        assertEquals(2, latest.size());
        assertEquals(4, latest.get(0).timestamp());
        assertEquals(5, latest.get(1).timestamp());
    }

    @Test
    void emptyAndInvalidLimitsAreSafe() {
        var buffer = new HistoryBuffer(2);
        assertTrue(buffer.latest(0).isEmpty());
        assertTrue(buffer.latest(10).isEmpty());
    }
}

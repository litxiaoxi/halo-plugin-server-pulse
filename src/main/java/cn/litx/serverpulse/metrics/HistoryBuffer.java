package cn.litx.serverpulse.metrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Small in-memory ring buffer. Samples are cheap and intentionally not persisted.
 */
public class HistoryBuffer {

    private final int capacity;
    private final Deque<HistoryPoint> points;

    public HistoryBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.points = new ArrayDeque<>(capacity);
    }

    public synchronized void add(HistoryPoint point) {
        if (point == null) {
            return;
        }
        while (points.size() >= capacity) {
            points.removeFirst();
        }
        points.addLast(point);
    }

    public synchronized List<HistoryPoint> latest(int max) {
        if (max <= 0 || points.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<HistoryPoint>(Math.min(max, points.size()));
        var iterator = points.descendingIterator();
        while (iterator.hasNext() && result.size() < max) {
            result.add(iterator.next());
        }
        java.util.Collections.reverse(result);
        return List.copyOf(result);
    }

    public synchronized int size() {
        return points.size();
    }

    public synchronized void clear() {
        points.clear();
    }
}

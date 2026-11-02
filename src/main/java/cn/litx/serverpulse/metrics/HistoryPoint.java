package cn.litx.serverpulse.metrics;

public record HistoryPoint(
    long timestamp,
    double cpuPercent,
    double memoryPercent,
    double storagePercent
) {
}

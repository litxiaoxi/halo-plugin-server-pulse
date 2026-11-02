package cn.litx.serverpulse.metrics;

public record Usage(
    double cpuPercent,
    double memoryPercent,
    double storagePercent,
    long memoryUsedBytes,
    long memoryTotalBytes,
    long storageUsedBytes,
    long storageTotalBytes,
    long uptimeSeconds
) {
}

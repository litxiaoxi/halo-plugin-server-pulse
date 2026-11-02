package cn.litx.serverpulse.metrics;

public record StorageInfo(
    String model,
    String mount,
    int diskCount,
    long totalBytes,
    long swapTotalBytes
) {
}

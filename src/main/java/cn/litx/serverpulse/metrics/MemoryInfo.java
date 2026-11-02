package cn.litx.serverpulse.metrics;

public record MemoryInfo(
    long totalBytes,
    String type
) {
}

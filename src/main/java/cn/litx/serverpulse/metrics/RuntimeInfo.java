package cn.litx.serverpulse.metrics;

public record RuntimeInfo(
    long jvmUptimeSeconds,
    long heapUsedBytes,
    long heapMaxBytes
) {
}

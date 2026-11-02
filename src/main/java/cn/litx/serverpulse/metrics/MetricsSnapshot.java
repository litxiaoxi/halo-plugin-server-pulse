package cn.litx.serverpulse.metrics;

public record MetricsSnapshot(
    long generatedAt,
    boolean degraded,
    StaticInfo info,
    Usage usage,
    RuntimeInfo runtime
) {
}

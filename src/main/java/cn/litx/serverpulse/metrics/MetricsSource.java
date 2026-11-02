package cn.litx.serverpulse.metrics;

/**
 * Blocking metric source. Implementations are only called from the background sampler,
 * never directly from a WebFlux request thread.
 */
public interface MetricsSource {

    String name();

    boolean degraded();

    StaticInfo readStaticInfo();

    Usage readUsage();
}

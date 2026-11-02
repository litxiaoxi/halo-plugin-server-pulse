package cn.litx.serverpulse.endpoint;

import java.util.List;
import cn.litx.serverpulse.metrics.HistoryPoint;
import cn.litx.serverpulse.metrics.RuntimeInfo;
import cn.litx.serverpulse.metrics.StaticInfo;
import cn.litx.serverpulse.metrics.Usage;

/**
 * Public read-only response returned to the ServerPulse page.
 */
public record MetricsResponse(
    String serverName,
    String pluginVersion,
    long generatedAt,
    boolean stale,
    boolean degraded,
    AccessInfo access,
    UiSettings ui,
    StaticInfo info,
    Usage usage,
    RuntimeInfo runtime,
    List<HistoryPoint> history
) {

    public record AccessInfo(String mode, boolean authenticated) {
    }

    public record UiSettings(
        String theme,
        int refreshInterval,
        boolean showHistory,
        boolean showDetails
    ) {
    }
}

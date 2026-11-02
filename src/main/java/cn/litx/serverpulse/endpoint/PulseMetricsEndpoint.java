package cn.litx.serverpulse.endpoint;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.plugin.PluginContext;
import cn.litx.serverpulse.config.PulseSettingsService;
import cn.litx.serverpulse.metrics.MetricsSnapshotCache;
import cn.litx.serverpulse.security.PulseAccessPolicy;

/**
 * Read-only public endpoint for the ServerPulse page.
 *
 * <p>The path is {@code /apis/api.serverpulse.halo.run/v1alpha1/metrics}. RBAC grants the
 * {@code get} verb to anonymous and authenticated visitors, while the access policy below
 * enforces the configured login-only mode and returns a useful 401 rather than hiding the
 * authorization decision in the browser.</p>
 */
@Component
@RequiredArgsConstructor
public class PulseMetricsEndpoint implements CustomEndpoint {

    private final PulseSettingsService settingsService;
    private final PulseAccessPolicy accessPolicy;
    private final MetricsSnapshotCache snapshotCache;
    private final PluginContext pluginContext;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("/metrics", this::getMetrics)
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("api.serverpulse.halo.run", "v1alpha1");
    }

    private Mono<ServerResponse> getMetrics(ServerRequest request) {
        return Mono.zip(
                settingsService.get(),
                accessPolicy.isAuthenticated(request.exchange())
            )
            .flatMap(tuple -> {
                var settings = tuple.getT1().normalized();
                var authenticated = tuple.getT2();
                if (!accessPolicy.allows(settings, authenticated)) {
                    return unauthorized();
                }

                var snapshotOptional = snapshotCache.current();
                if (snapshotOptional.isEmpty()) {
                    return warmingUp();
                }

                var snapshot = snapshotOptional.get();
                var now = System.currentTimeMillis();
                var response = new MetricsResponse(
                    settings.serverName(),
                    pluginContext.getVersion(),
                    snapshot.generatedAt(),
                    snapshotCache.isStale(snapshot, now),
                    snapshot.degraded(),
                    new MetricsResponse.AccessInfo(settings.accessMode(), authenticated),
                    new MetricsResponse.UiSettings(
                        settings.effectiveTheme(),
                        settings.refreshSeconds(),
                        settings.showHistoryEnabled(),
                        settings.showDetailsEnabled()
                    ),
                    settings.showDetailsEnabled() ? snapshot.info() : null,
                    snapshot.usage(),
                    settings.showDetailsEnabled() ? snapshot.runtime() : null,
                    settings.showHistoryEnabled() ? snapshotCache.history() : List.of()
                );

                return ServerResponse.ok()
                    .contentType(APPLICATION_JSON)
                    .cacheControl(CacheControl.noStore())
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Vary", "Cookie")
                    .bodyValue(response);
            });
    }

    private Mono<ServerResponse> unauthorized() {
        return ServerResponse.status(HttpStatus.UNAUTHORIZED)
            .contentType(APPLICATION_JSON)
            .cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .bodyValue(new ErrorResponse("unauthorized", "请先登录后查看服务器监控"));
    }

    private Mono<ServerResponse> warmingUp() {
        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(APPLICATION_JSON)
            .cacheControl(CacheControl.noStore())
            .header("Retry-After", "1")
            .bodyValue(new ErrorResponse("warming_up", "监控数据正在初始化"));
    }
}

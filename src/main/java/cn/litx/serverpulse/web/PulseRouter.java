package cn.litx.serverpulse.web;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.PluginContext;
import run.halo.app.theme.TemplateNameResolver;
import run.halo.app.theme.router.ModelConst;
import cn.litx.serverpulse.config.PulseSettingsService;

/**
 * Public page route for {@code /pulse}.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class PulseRouter {

    private final TemplateNameResolver templateNameResolver;
    private final PulseSettingsService settingsService;
    private final PluginContext pluginContext;

    @Bean
    public RouterFunction<ServerResponse> pulseRouterFunction() {
        return route()
            .GET("/pulse", this::render)
            .GET("/pulse/", this::render)
            .build();
    }

    private Mono<ServerResponse> render(ServerRequest request) {
        return settingsService.get().flatMap(settings -> {
            var model = new HashMap<String, Object>();
            model.put(ModelConst.TEMPLATE_ID, "plugin:" + pluginContext.getName() + ":pulse");
            model.put(ModelConst.NO_CACHE, true);
            model.put("pluginName", pluginContext.getName());
            model.put("pluginVersion", pluginContext.getVersion());
            model.put("pulseTheme", settings.effectiveTheme());
            model.put("apiBase", contextPath(request) + "/apis/api.serverpulse.halo.run/v1alpha1");

            return templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), "pulse")
                .flatMap(templateName -> ServerResponse.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Robots-Tag", "noindex, nofollow")
                    .header("Referrer-Policy", "no-referrer")
                    .header("X-Content-Type-Options", "nosniff")
                    .render(templateName, model));
        });
    }

    private static String contextPath(ServerRequest request) {
        return request.exchange().getRequest().getPath().contextPath().value();
    }
}

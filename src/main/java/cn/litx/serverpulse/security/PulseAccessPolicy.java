package cn.litx.serverpulse.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import cn.litx.serverpulse.config.PulseSettings;

/**
 * Central place for the "login-only vs public" decision.
 *
 * <p>The public API is granted to anonymous visitors in RBAC so the endpoint can return
 * a useful 401/403 response. The actual data decision is still made here and fails closed.</p>
 */
@Component
@RequiredArgsConstructor
public class PulseAccessPolicy {

    private final ObjectProvider<ServerSecurityContextRepository> contextRepositoryProvider;

    public boolean allows(PulseSettings settings, boolean authenticated) {
        return settings.publicAccess() || authenticated;
    }

    public Mono<Boolean> isAuthenticated(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(this::isRealUser)
            .defaultIfEmpty(false)
            .onErrorResume(error -> Mono.just(false))
            .flatMap(authenticated -> {
                if (authenticated) {
                    return Mono.just(true);
                }
                var repository = contextRepositoryProvider.getIfAvailable();
                if (repository == null) {
                    return Mono.just(false);
                }
                return repository.load(exchange)
                    .map(SecurityContext::getAuthentication)
                    .map(this::isRealUser)
                    .defaultIfEmpty(false)
                    .onErrorResume(error -> Mono.just(false));
            });
    }

    private boolean isRealUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return !"anonymousUser".equals(authentication.getName());
    }
}

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
 * Central place for the access mode decision.
 *
 * <p>The public API is granted to anonymous visitors in RBAC so the endpoint can return
 * a useful 401/403 response. The actual data decision is still made here and fails closed.</p>
 */
@Component
@RequiredArgsConstructor
public class PulseAccessPolicy {

    private final ObjectProvider<ServerSecurityContextRepository> contextRepositoryProvider;

    /**
     * Decides whether the given user may read metrics.
     *
     * @param username the authenticated username, or an empty string for anonymous visitors
     */
    public boolean allows(PulseSettings settings, String username) {
        var normalized = settings.normalized();
        var authenticated = username != null && !username.isBlank();
        return switch (normalized.accessMode()) {
            case PulseSettings.ACCESS_PUBLIC -> true;
            case PulseSettings.ACCESS_USERS -> authenticated
                && normalized.allowedUsers().contains(username);
            default -> authenticated;
        };
    }

    /**
     * Resolves the current username, falling back to the security context repository when
     * the reactive context has not been populated yet. Returns an empty string for anonymous.
     */
    public Mono<String> currentUsername(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(this::isRealUser)
            .map(Authentication::getName)
            .filter(name -> !name.isBlank())
            .switchIfEmpty(Mono.defer(() -> loadUsername(exchange)))
            .defaultIfEmpty("")
            .onErrorResume(error -> Mono.just(""));
    }

    private Mono<String> loadUsername(ServerWebExchange exchange) {
        var repository = contextRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return Mono.empty();
        }
        return repository.load(exchange)
            .map(SecurityContext::getAuthentication)
            .filter(this::isRealUser)
            .map(Authentication::getName);
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

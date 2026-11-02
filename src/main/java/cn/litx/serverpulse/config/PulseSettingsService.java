package cn.litx.serverpulse.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

/**
 * Reads ServerPulse settings through Halo's cached setting fetcher.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PulseSettingsService {

    private final ReactiveSettingFetcher settingFetcher;

    public Mono<PulseSettings> get() {
        return settingFetcher.fetch(PulseSettings.GROUP, PulseSettings.class)
            .map(PulseSettings::normalized)
            .defaultIfEmpty(PulseSettings.defaults())
            .onErrorResume(error -> {
                // Never turn a configuration read failure into public access.
                log.warn("Failed to read ServerPulse settings, falling back to login-only defaults: {}",
                    error.toString());
                return Mono.just(PulseSettings.defaults());
            });
    }
}

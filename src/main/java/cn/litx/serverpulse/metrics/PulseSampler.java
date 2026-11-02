package cn.litx.serverpulse.metrics;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Samples host metrics in the background and publishes immutable snapshots to the cache.
 *
 * <p>The sampler deliberately never sleeps on the request path. OSHI calls run on Reactor's
 * bounded elastic scheduler so Halo's event loop is not blocked.</p>
 */
@Slf4j
@Component
public class PulseSampler {

    private static final long STATIC_INFO_TTL_MS = 60_000L;

    private final MetricsSnapshotCache cache;
    private final AtomicBoolean sampling = new AtomicBoolean(false);

    private volatile MetricsSource source;
    private volatile StaticInfo staticInfo;
    private volatile long staticLoadedAt;

    public PulseSampler(MetricsSource source, MetricsSnapshotCache cache) {
        this.source = source;
        this.cache = cache;
    }

    @Scheduled(fixedRate = 2000, initialDelay = 0)
    public Mono<Void> sample() {
        if (!sampling.compareAndSet(false, true)) {
            return Mono.empty();
        }
        return Mono.fromRunnable(this::sampleOnce)
            .subscribeOn(Schedulers.boundedElastic())
            .doOnError(error -> log.warn("ServerPulse metric sampling failed: {}", error.toString()))
            .onErrorResume(error -> Mono.empty())
            .doFinally(signal -> sampling.set(false))
            .then();
    }

    private void sampleOnce() {
        try {
            sampleWith(source);
        } catch (Throwable error) {
            if (!(source instanceof JvmMetricsSource)) {
                log.warn("OSHI sampling failed, switching to JVM metrics: {}", error.toString());
                source = new JvmMetricsSource();
                staticInfo = null;
                sampleWith(source);
            } else {
                log.warn("JVM metric sampling failed: {}", error.toString());
            }
        }
    }

    private void sampleWith(MetricsSource metricsSource) {
        var now = System.currentTimeMillis();
        if (staticInfo == null || now - staticLoadedAt > STATIC_INFO_TTL_MS) {
            staticInfo = metricsSource.readStaticInfo();
            staticLoadedAt = now;
        }
        var usage = metricsSource.readUsage();
        var snapshot = new MetricsSnapshot(
            now,
            metricsSource.degraded(),
            staticInfo,
            usage,
            runtimeInfo()
        );
        cache.update(snapshot);
    }

    private RuntimeInfo runtimeInfo() {
        var memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var max = memory.getMax();
        if (max <= 0) {
            max = -1L;
        }
        return new RuntimeInfo(
            ManagementFactory.getRuntimeMXBean().getUptime() / 1000L,
            Math.max(0L, memory.getUsed()),
            max
        );
    }
}

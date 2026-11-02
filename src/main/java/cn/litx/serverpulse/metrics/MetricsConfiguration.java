package cn.litx.serverpulse.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the best available metric source at plugin startup.
 *
 * <p>OSHI is preferred because it provides CPU model, OS and disk details. If its
 * native bridge cannot be loaded in the plugin class loader, the plugin still starts
 * with a reduced JVM-only view instead of failing the whole Halo plugin.</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class MetricsConfiguration {

    @Bean
    public MetricsSource metricsSource() {
        try {
            return new OshiMetricsSource();
        } catch (Throwable error) {
            log.warn("OSHI is unavailable, falling back to JVM metrics: {}", error.toString());
            return new JvmMetricsSource();
        }
    }
}

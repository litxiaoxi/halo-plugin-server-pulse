package cn.litx.serverpulse;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the plugin-local scheduler used by {@code PulseSampler}.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ServerPulseSchedulingConfiguration {
}

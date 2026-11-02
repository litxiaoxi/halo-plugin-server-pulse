package cn.litx.serverpulse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * ServerPulse plugin entry point.
 *
 * <p>The plugin keeps all runtime state in memory. Halo owns the plugin settings and
 * lifecycle, so no persistent data is created or removed here.</p>
 */
@Slf4j
@Component
public class ServerPulsePlugin extends BasePlugin {

    public ServerPulsePlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
        log.info("ServerPulse plugin {} v{} started", context.getName(), context.getVersion());
    }

    @Override
    public void stop() {
        log.info("ServerPulse plugin {} v{} stopped", context.getName(), context.getVersion());
    }
}

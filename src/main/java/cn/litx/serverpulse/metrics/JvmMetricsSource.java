package cn.litx.serverpulse.metrics;

import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.Locale;

/**
 * Conservative fallback used when OSHI/JNA cannot be loaded. It exposes only what the
 * JDK can provide through supported management APIs.
 */
public class JvmMetricsSource implements MetricsSource {

    private final java.lang.management.OperatingSystemMXBean osBean;
    private final OperatingSystemMXBean extendedOsBean;
    private final Runtime runtime = Runtime.getRuntime();

    public JvmMetricsSource() {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.extendedOsBean = osBean instanceof OperatingSystemMXBean bean ? bean : null;
    }

    @Override
    public String name() {
        return "jvm";
    }

    @Override
    public boolean degraded() {
        return true;
    }

    @Override
    public StaticInfo readStaticInfo() {
        var arch = System.getProperty("os.arch", "");
        var bitness = arch.contains("64") ? 64 : 32;
        var processorInfo = new ProcessorInfo(
            "N/A",
            Math.max(1, osBean.getAvailableProcessors()),
            0L,
            bitness,
            arch
        );
        var osInfo = new OsInfo(
            System.getProperty("os.name", ""),
            System.getProperty("os.version", ""),
            "",
            bitness
        );
        var totalMemory = totalMemory();
        var response = new MemoryInfo(totalMemory, "");
        var rootStore = rootStore();
        var totalStorage = safeTotalSpace(rootStore);
        var storageInfo = new StorageInfo(
            "Root filesystem",
            rootStore == null ? "" : rootStore.name(),
            1,
            totalStorage,
            0L
        );
        return new StaticInfo(processorInfo, osInfo, response, storageInfo, 0);
    }

    @Override
    public Usage readUsage() {
        var totalMemory = totalMemory();
        var usedMemory = usedMemory(totalMemory);
        var rootStore = rootStore();
        var totalStorage = safeTotalSpace(rootStore);
        var usableStorage = safeUsableSpace(rootStore);
        var usedStorage = Math.max(0L, totalStorage - usableStorage);

        return new Usage(
            cpuPercent(),
            percent(usedMemory, totalMemory),
            percent(usedStorage, totalStorage),
            usedMemory,
            totalMemory,
            usedStorage,
            totalStorage,
            ManagementFactory.getRuntimeMXBean().getUptime() / 1000L
        );
    }

    private long totalMemory() {
        if (extendedOsBean != null) {
            var total = extendedOsBean.getTotalMemorySize();
            if (total > 0) {
                return total;
            }
        }
        return runtime.maxMemory();
    }

    private long usedMemory(long total) {
        if (extendedOsBean != null) {
            var free = extendedOsBean.getFreeMemorySize();
            if (free >= 0 && total > 0) {
                return Math.max(0L, total - free);
            }
        }
        var used = runtime.totalMemory() - runtime.freeMemory();
        return Math.max(0L, Math.min(total, used));
    }

    private double cpuPercent() {
        if (extendedOsBean != null) {
            var load = extendedOsBean.getSystemCpuLoad();
            if (load >= 0) {
                return clampPercent(load * 100.0);
            }
        }
        var average = osBean.getSystemLoadAverage();
        if (average >= 0) {
            return clampPercent(average / Math.max(1, osBean.getAvailableProcessors()) * 100.0);
        }
        return 0.0;
    }

    private FileStore rootStore() {
        try {
            FileStore first = null;
            var systemDrive = System.getenv("SystemDrive");
            for (FileStore store : FileSystems.getDefault().getFileStores()) {
                if (first == null) {
                    first = store;
                }
                var name = store.name().toLowerCase(Locale.ROOT);
                if (systemDrive != null && name.startsWith(systemDrive.toLowerCase(Locale.ROOT))) {
                    return store;
                }
                if ("/".equals(store.name())) {
                    return store;
                }
            }
            return first;
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private static long safeTotalSpace(FileStore store) {
        if (store == null) {
            return 0L;
        }
        try {
            return Math.max(0L, store.getTotalSpace());
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static long safeUsableSpace(FileStore store) {
        if (store == null) {
            return 0L;
        }
        try {
            return Math.max(0L, store.getUsableSpace());
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static double percent(long used, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return clampPercent((used * 100.0) / total);
    }

    private static double clampPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.round(Math.max(0.0, Math.min(100.0, value)) * 10.0) / 10.0;
    }
}

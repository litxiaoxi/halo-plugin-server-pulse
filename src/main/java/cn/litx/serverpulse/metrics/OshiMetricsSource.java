package cn.litx.serverpulse.metrics;

import java.util.List;
import java.util.Locale;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.PhysicalMemory;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

/**
 * OSHI-based metrics source. All methods are blocking and are only called by the sampler.
 */
public class OshiMetricsSource implements MetricsSource {

    private final SystemInfo systemInfo;
    private final CentralProcessor processor;
    private final GlobalMemory memory;
    private final OperatingSystem operatingSystem;
    private long[] previousTicks;

    public OshiMetricsSource() {
        this.systemInfo = new SystemInfo();
        this.processor = systemInfo.getHardware().getProcessor();
        this.memory = systemInfo.getHardware().getMemory();
        this.operatingSystem = systemInfo.getOperatingSystem();
        this.previousTicks = processor.getSystemCpuLoadTicks();
    }

    @Override
    public String name() {
        return "oshi";
    }

    @Override
    public boolean degraded() {
        return false;
    }

    @Override
    public StaticInfo readStaticInfo() {
        var hardware = systemInfo.getHardware();
        var processorIdentifier = processor.getProcessorIdentifier();

        var cpuName = processorIdentifier.getName() == null
            ? "" : processorIdentifier.getName().trim();
        var atIndex = cpuName.indexOf('@');
        if (atIndex > 0) {
            cpuName = cpuName.substring(0, atIndex).trim();
        }

        var clockHz = processor.getMaxFreq();
        if (clockHz <= 0) {
            clockHz = processorIdentifier.getVendorFreq();
        }

        var processorInfo = new ProcessorInfo(
            cpuName,
            processor.getLogicalProcessorCount(),
            Math.max(0L, clockHz),
            processorIdentifier.isCpu64bit() ? 64 : 32,
            System.getProperty("os.arch", "")
        );

        var versionInfo = operatingSystem.getVersionInfo();
        var osInfo = new OsInfo(
            nullToEmpty(operatingSystem.getFamily()),
            nullToEmpty(versionInfo.getVersion()),
            nullToEmpty(versionInfo.getCodeName()),
            operatingSystem.getBitness()
        );

        var memoryType = memory.getPhysicalMemory().stream()
            .findFirst()
            .map(PhysicalMemory::getMemoryType)
            .orElse("");
        var memoryInfo = new MemoryInfo(memory.getTotal(), memoryType);

        var diskStores = hardware.getDiskStores();
        var diskModel = diskStores.stream()
            .findFirst()
            .map(HWDiskStore::getModel)
            .orElse("");
        var diskMarker = diskModel.indexOf("(Standard disk drives)");
        if (diskMarker > 0) {
            diskModel = diskModel.substring(0, diskMarker).trim();
        }
        var rootStore = findRootStore();
        var physicalDiskBytes = diskStores.stream().mapToLong(HWDiskStore::getSize).sum();
        var diskTotal = physicalDiskBytes > 0
            ? physicalDiskBytes
            : (rootStore == null ? 0L : rootStore.getTotalSpace());
        var storageInfo = new StorageInfo(
            diskModel.isBlank() ? "Root filesystem" : diskModel.trim(),
            rootStore == null ? "" : nullToEmpty(rootStore.getMount()),
            diskStores.size(),
            diskTotal,
            memory.getVirtualMemory().getSwapTotal()
        );

        return new StaticInfo(
            processorInfo,
            osInfo,
            memoryInfo,
            storageInfo,
            operatingSystem.getProcessCount()
        );
    }

    @Override
    public Usage readUsage() {
        var tickSnapshot = processor.getSystemCpuLoadTicks();
        var cpu = processor.getSystemCpuLoadBetweenTicks(previousTicks) * 100.0;
        previousTicks = tickSnapshot;

        var memoryTotal = memory.getTotal();
        var memoryAvailable = memory.getAvailable();
        var memoryUsed = Math.max(0L, memoryTotal - memoryAvailable);
        var memoryPercent = percent(memoryUsed, memoryTotal);

        var rootStore = findRootStore();
        var storageTotal = rootStore == null ? 0L : Math.max(0L, rootStore.getTotalSpace());
        var storageUsable = rootStore == null ? 0L : Math.max(0L, rootStore.getUsableSpace());
        var storageUsed = Math.max(0L, storageTotal - storageUsable);
        var storagePercent = percent(storageUsed, storageTotal);

        return new Usage(
            clampPercent(cpu),
            memoryPercent,
            storagePercent,
            memoryUsed,
            memoryTotal,
            storageUsed,
            storageTotal,
            Math.max(0L, operatingSystem.getSystemUptime())
        );
    }

    private OSFileStore findRootStore() {
        List<OSFileStore> stores = operatingSystem.getFileSystem().getFileStores();
        if (stores.isEmpty()) {
            return null;
        }

        var systemDrive = System.getenv("SystemDrive");
        if (systemDrive != null && !systemDrive.isBlank()) {
            var normalizedDrive = systemDrive.toLowerCase(Locale.ROOT);
            for (var store : stores) {
                var mount = nullToEmpty(store.getMount()).toLowerCase(Locale.ROOT);
                if (mount.equals(normalizedDrive) || mount.startsWith(normalizedDrive + "\\")
                    || mount.startsWith(normalizedDrive + "/")) {
                    return store;
                }
            }
        }

        for (var store : stores) {
            if ("/".equals(store.getMount())) {
                return store;
            }
        }

        for (var store : stores) {
            if (store.getTotalSpace() > 0) {
                return store;
            }
        }
        return stores.getFirst();
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

package cn.litx.serverpulse.metrics;

public record StaticInfo(
    ProcessorInfo processor,
    OsInfo os,
    MemoryInfo memory,
    StorageInfo storage,
    int processCount
) {
}

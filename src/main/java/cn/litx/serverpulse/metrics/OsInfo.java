package cn.litx.serverpulse.metrics;

public record OsInfo(
    String family,
    String version,
    String codeName,
    int bitness
) {
}

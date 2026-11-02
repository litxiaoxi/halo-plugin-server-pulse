package cn.litx.serverpulse.metrics;

public record ProcessorInfo(
    String name,
    int cores,
    long clockHz,
    int bitDepth,
    String architecture
) {
}

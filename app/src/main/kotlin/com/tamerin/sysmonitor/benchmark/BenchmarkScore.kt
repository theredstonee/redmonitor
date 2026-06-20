package com.tamerin.sysmonitor.benchmark

/**
 * Shared duration presets + score normalization for all benchmarks.
 *
 * Score scaling targets AnTuTu's familiar range so users can intuitively compare:
 *  - entry-level phone        ~ 100k–250k
 *  - upper mid-range          ~ 400k–700k
 *  - flagship (SD8G3, A17)    ~ 1.2M–1.8M
 *
 * We do this by calibrating each sub-score against a reference throughput
 * that a "score = 100k" device would produce. The total benchmark score
 * is a weighted sum, NOT a multiplication — that way one extremely fast
 * subsystem can't hide a slow one (matches AnTuTu's behavior).
 */
enum class BenchmarkDuration(val seconds: Int, val short: String) {
    QUICK(30, "30 s"),
    NORMAL(60, "1 min"),
    LONG(120, "2 min"),
    MARATHON(300, "5 min")
}

/**
 * Raw throughput → normalized score.
 *
 * @param value      measured raw value (ops/sec, MB/s, IOPS, etc.)
 * @param reference  value at which the device should land on 100,000 points
 */
fun normalize(value: Double, reference: Double): Int {
    if (reference <= 0.0) return 0
    return (value / reference * 100_000.0).toInt().coerceAtLeast(0)
}

/**
 * Reference values calibrated against a Pixel 6 / SD888-class device.
 * Anything faster scores higher, anything slower scores lower. Linear
 * scale so a 2× faster device gets 2× the points.
 */
object BenchmarkReferences {
    // CPU sub-tests (operations per second, single-core)
    const val CPU_INT_REF_OPS = 350_000_000.0    // Snapdragon 888 ish
    const val CPU_FLOAT_REF_OPS = 280_000_000.0
    const val CPU_CRYPTO_REF_HASHES = 8_000_000.0
    const val CPU_SORT_REF_ELEMENTS = 60_000_000.0

    // RAM bandwidth references (MB/s), per tier
    const val RAM_L1_REF_MBS = 30_000.0
    const val RAM_L2_REF_MBS = 18_000.0
    const val RAM_L3_REF_MBS = 12_000.0
    const val RAM_DRAM_REF_MBS = 8_000.0

    // Storage (MB/s + IOPS)
    const val STORAGE_SEQ_WRITE_REF_MBS = 1_500.0
    const val STORAGE_SEQ_READ_REF_MBS = 2_000.0
    const val STORAGE_RAND_QD1_REF_IOPS = 18_000.0
    const val STORAGE_RAND_QD16_REF_IOPS = 80_000.0

    // GPU
    const val GPU_VERTEX_REF_PER_SEC = 250_000_000.0
    const val GPU_FILLRATE_REF_GPIXELS = 25.0
    const val GPU_SHADER_REF_FRAMES = 8_000.0
}

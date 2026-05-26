package com.tamerin.sysmonitor.benchmark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureNanoTime

data class ImageBenchResult(
    val megapixelsPerSec: Double,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val passes: Int
)

object ImageBenchmark {
    private const val W = 1920
    private const val H = 1080
    private const val PASSES = 6

    suspend fun run(): ImageBenchResult = withContext(Dispatchers.Default) {
        val src = IntArray(W * H) { it * 31 xor (it shl 7) }
        val dst = IntArray(W * H)
        val ns = measureNanoTime {
            repeat(PASSES) {
                // 3x3 box blur — pure CPU, no allocations
                for (y in 1 until H - 1) {
                    val yw = y * W
                    for (x in 1 until W - 1) {
                        var r = 0; var g = 0; var b = 0
                        for (dy in -1..1) {
                            val dyw = (y + dy) * W
                            for (dx in -1..1) {
                                val p = src[dyw + x + dx]
                                r += (p ushr 16) and 0xFF
                                g += (p ushr 8) and 0xFF
                                b += p and 0xFF
                            }
                        }
                        dst[yw + x] = ((r / 9) shl 16) or ((g / 9) shl 8) or (b / 9)
                    }
                }
            }
        }
        val pixels = W.toLong() * H * PASSES
        val seconds = ns / 1e9
        val mpps = (pixels / 1_000_000.0) / seconds
        ImageBenchResult(
            megapixelsPerSec = mpps,
            durationMs = ns / 1_000_000L,
            width = W,
            height = H,
            passes = PASSES
        )
    }
}

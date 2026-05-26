package com.tamerin.sysmonitor.data

import android.os.Build
import java.io.File

data class SocInfo(
    val marketingName: String,
    val code: String,
    val manufacturer: String,
    val fullLabel: String
)

object SocIdentifier {

    fun identify(): SocInfo {
        val mfg = manufacturer()
        val code = socCode()
        val marketing = marketingNameFor(code, mfg)
        val full = when {
            marketing != null && marketing != code -> "$marketing ($code)"
            code.isNotBlank() -> code
            else -> "Unbekannt"
        }
        return SocInfo(
            marketingName = marketing ?: code,
            code = code,
            manufacturer = mfg,
            fullLabel = full
        )
    }

    private fun manufacturer(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val m = Build.SOC_MANUFACTURER
            if (!m.isNullOrBlank() && m != Build.UNKNOWN) return m
        }
        // Heuristic from board
        val board = (Build.BOARD ?: "").lowercase()
        return when {
            board.contains("kona") || board.contains("lahaina") || board.startsWith("sm") -> "Qualcomm"
            board.contains("exynos") || board.startsWith("erd") -> "Samsung"
            board.contains("mt") -> "MediaTek"
            board.contains("gs") -> "Google"
            else -> Build.HARDWARE ?: "Unbekannt"
        }
    }

    private fun socCode(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val m = Build.SOC_MODEL
            if (!m.isNullOrBlank() && m != Build.UNKNOWN) return m
        }
        // /proc/cpuinfo "Hardware" line
        val hw = runCatching {
            File("/proc/cpuinfo").readLines()
                .firstOrNull { it.startsWith("Hardware") }
                ?.substringAfter(":")?.trim()
        }.getOrNull()
        if (!hw.isNullOrBlank()) return hw
        return Build.HARDWARE ?: ""
    }

    private fun marketingNameFor(code: String, manufacturer: String): String? {
        val upper = code.uppercase()
        // Qualcomm Snapdragon
        QC_MAP[upper]?.let { return it }
        // Samsung Exynos
        EXYNOS_MAP[upper]?.let { return it }
        // MediaTek
        MT_MAP[upper]?.let { return it }
        // Google Tensor
        TENSOR_MAP[upper]?.let { return it }
        return null
    }

    // Qualcomm SM/SDM codes → Marketing names (most common modern SoCs)
    private val QC_MAP = mapOf(
        // 8-series flagship
        "SM8750" to "Snapdragon 8 Elite",
        "SM8650" to "Snapdragon 8 Gen 3",
        "SM8550" to "Snapdragon 8 Gen 2",
        "SM8475" to "Snapdragon 8+ Gen 1",
        "SM8450" to "Snapdragon 8 Gen 1",
        "SM8350" to "Snapdragon 888",
        "SM8325" to "Snapdragon 888+",
        "SM8250" to "Snapdragon 865",
        "SM8150" to "Snapdragon 855",
        "SDM855" to "Snapdragon 855",
        "MSM8998" to "Snapdragon 835",
        // 7-series upper-mid
        "SM7675" to "Snapdragon 7+ Gen 3",
        "SM7550" to "Snapdragon 7 Gen 3",
        "SM7450" to "Snapdragon 7 Gen 1",
        "SM7350" to "Snapdragon 780G",
        "SM7325" to "Snapdragon 778G",
        "SM7250" to "Snapdragon 765G",
        "SM7150" to "Snapdragon 730",
        // 6-series mid
        "SM6650" to "Snapdragon 6 Gen 3",
        "SM6450" to "Snapdragon 6 Gen 1",
        "SM6375" to "Snapdragon 695",
        "SM6225" to "Snapdragon 680",
        "SM6125" to "Snapdragon 665",
        "SDM660" to "Snapdragon 660",
        // 4-series budget
        "SM4450" to "Snapdragon 4 Gen 2",
        "SM4375" to "Snapdragon 4 Gen 1",
        "SM4350" to "Snapdragon 480",
        "SM4250" to "Snapdragon 460",
        // Codenames sometimes used
        "KALAMA" to "Snapdragon 8 Gen 2",
        "LAHAINA" to "Snapdragon 888",
        "KONA" to "Snapdragon 865",
        "WAIPIO" to "Snapdragon 8 Gen 1",
        "TARO" to "Snapdragon 8 Gen 2",
        "PINEAPPLE" to "Snapdragon 8 Gen 3",
        "SUN" to "Snapdragon 8 Elite"
    )

    // Samsung Exynos
    private val EXYNOS_MAP = mapOf(
        "EXYNOS2400" to "Exynos 2400",
        "S5E9945" to "Exynos 2400",
        "EXYNOS2200" to "Exynos 2200",
        "S5E9925" to "Exynos 2200",
        "EXYNOS2100" to "Exynos 2100",
        "S5E9840" to "Exynos 2100",
        "EXYNOS1480" to "Exynos 1480",
        "EXYNOS1380" to "Exynos 1380",
        "EXYNOS1330" to "Exynos 1330",
        "EXYNOS1280" to "Exynos 1280",
        "EXYNOS990" to "Exynos 990",
        "EXYNOS9820" to "Exynos 9820",
        "EXYNOS9810" to "Exynos 9810"
    )

    // MediaTek Dimensity / Helio
    private val MT_MAP = mapOf(
        "MT6989" to "Dimensity 9300",
        "MT6989W" to "Dimensity 9300+",
        "MT6985" to "Dimensity 9200",
        "MT6983" to "Dimensity 9000",
        "MT6886" to "Dimensity 8200",
        "MT6877" to "Dimensity 1100",
        "MT6873" to "Dimensity 800",
        "MT6853" to "Dimensity 720",
        "MT6833" to "Dimensity 700",
        "MT6789" to "Helio G99",
        "MT6785" to "Helio G90T",
        "MT6781" to "Helio G96",
        "MT6779" to "Helio P90"
    )

    // Google Tensor
    private val TENSOR_MAP = mapOf(
        "GS101" to "Tensor G1",
        "GS201" to "Tensor G2",
        "ZUMA" to "Tensor G3",
        "GS301" to "Tensor G3",
        "ZUMAPRO" to "Tensor G4",
        "GS401" to "Tensor G4"
    )
}

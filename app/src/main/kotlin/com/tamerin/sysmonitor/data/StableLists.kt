package com.tamerin.sysmonitor.data

import androidx.compose.runtime.Immutable

/**
 * Compose's strong-skipping treats `List<X>` as UNSTABLE and falls back to
 * instance equality. A fresh List instance with the same content STILL
 * triggers recomposition.
 *
 * Wrapping a list in this @Immutable holder gives Compose a stable type
 * whose `equals()` compares CONTENT (data class default) → recomposition
 * skipped when values stayed identical between two ticks (very common for
 * per-core CPU samples on an idle phone).
 */
@Immutable
data class StableFloatList(val items: List<Float>) {
    val size: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()
    operator fun get(index: Int): Float = items[index]
    fun average(): Double = items.average()
}

@Immutable
data class StableLongList(val items: List<Long>) {
    val size: Int get() = items.size
    fun getOrZero(index: Int): Long = items.getOrNull(index) ?: 0L
    fun anyPositive(): Boolean = items.any { it > 0 }
}

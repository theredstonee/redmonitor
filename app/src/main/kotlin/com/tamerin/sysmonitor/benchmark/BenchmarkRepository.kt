package com.tamerin.sysmonitor.benchmark

import android.content.Context
import androidx.compose.runtime.Immutable
import com.tamerin.sysmonitor.benchmark.db.BenchmarkDao
import com.tamerin.sysmonitor.benchmark.db.BenchmarkDatabase
import com.tamerin.sysmonitor.benchmark.db.BenchmarkRunWithSubs
import com.tamerin.sysmonitor.benchmark.db.BenchmarkType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight observable state for an in-flight benchmark. The fullscreen
 * run-UI subscribes to this so progress survives Activity recreation /
 * config changes. Cross-process update path goes through here as well —
 * the BenchmarkService (which runs in its own :bench process) posts to
 * its own copy of this singleton; the main-process copy is updated via
 * Messenger/IPC layer.
 */
@Immutable
sealed class BenchmarkState {
    data object Idle : BenchmarkState()

    @Immutable
    data class Running(
        val type: BenchmarkType,
        val phaseLabel: String,
        val doneSeconds: Int,
        val totalSeconds: Int,
        val currentScore: Int = 0,
        val partialSubScores: List<PartialSub> = emptyList()
    ) : BenchmarkState() {
        val progress: Float get() = if (totalSeconds > 0) doneSeconds.toFloat() / totalSeconds else 0f
    }

    @Immutable
    data class Done(val runId: Long, val type: BenchmarkType, val totalScore: Int) : BenchmarkState()

    @Immutable
    data class Error(val type: BenchmarkType, val message: String) : BenchmarkState()
}

@Immutable
data class PartialSub(
    val name: String,
    val singleScore: Int,
    val multiScore: Int
)

object BenchmarkRepository {

    private val _state = MutableStateFlow<BenchmarkState>(BenchmarkState.Idle)
    val state: StateFlow<BenchmarkState> = _state.asStateFlow()

    @Volatile
    private var dao: BenchmarkDao? = null

    fun dao(context: Context): BenchmarkDao {
        return dao ?: synchronized(this) {
            dao ?: BenchmarkDatabase.get(context).benchmarkDao().also { dao = it }
        }
    }

    fun history(context: Context, type: BenchmarkType? = null): Flow<List<BenchmarkRunWithSubs>> {
        val d = dao(context)
        return if (type == null) d.observeAllRuns() else d.observeRunsForType(type)
    }

    /** Called by BenchmarkService (in either process) when state changes. */
    fun publish(newState: BenchmarkState) {
        _state.value = newState
    }

    fun reset() {
        _state.value = BenchmarkState.Idle
    }
}

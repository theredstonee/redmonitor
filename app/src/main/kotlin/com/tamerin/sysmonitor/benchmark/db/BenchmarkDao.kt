package com.tamerin.sysmonitor.benchmark.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class BenchmarkRunWithSubs(
    @Embedded val run: BenchmarkRun,
    @Relation(parentColumn = "id", entityColumn = "run_id")
    val subScores: List<BenchmarkSubScore>
)

@Dao
interface BenchmarkDao {

    @Insert
    suspend fun insertRun(run: BenchmarkRun): Long

    @Insert
    suspend fun insertSubScores(subs: List<BenchmarkSubScore>)

    @Transaction
    suspend fun insertRunWithSubs(run: BenchmarkRun, subs: List<BenchmarkSubScore>): Long {
        val id = insertRun(run)
        if (subs.isNotEmpty()) {
            insertSubScores(subs.map { it.copy(runId = id) })
        }
        return id
    }

    @Transaction
    @Query("SELECT * FROM benchmark_runs ORDER BY timestamp DESC LIMIT :limit")
    fun observeAllRuns(limit: Int = 200): Flow<List<BenchmarkRunWithSubs>>

    @Transaction
    @Query("SELECT * FROM benchmark_runs WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    fun observeRunsForType(type: BenchmarkType, limit: Int = 200): Flow<List<BenchmarkRunWithSubs>>

    @Transaction
    @Query("SELECT * FROM benchmark_runs WHERE id = :id")
    suspend fun getRun(id: Long): BenchmarkRunWithSubs?

    @Query("DELETE FROM benchmark_runs WHERE id = :id")
    suspend fun deleteRun(id: Long)

    @Query("DELETE FROM benchmark_runs")
    suspend fun deleteAll()

    @Query("SELECT MAX(totalScore) FROM benchmark_runs WHERE type = :type")
    suspend fun bestScoreForType(type: BenchmarkType): Int?
}

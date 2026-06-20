package com.tamerin.sysmonitor.benchmark.db

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BenchmarkType(val displayName: String) {
    CPU("CPU"),
    RAM("RAM"),
    STORAGE_SEQUENTIAL("Storage Seq."),
    STORAGE_RANDOM("Storage Random"),
    GPU("GPU"),
    IMAGE("Bild-Verarbeitung")
}

@Immutable
@Entity(tableName = "benchmark_runs")
data class BenchmarkRun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: BenchmarkType,
    val timestamp: Long,
    val totalScore: Int,
    val singleScore: Int? = null,
    val multiScore: Int? = null,
    val durationMs: Long,
    val phaseSeconds: Int,
    @ColumnInfo(name = "device_model") val deviceModel: String,
    @ColumnInfo(name = "android_sdk") val androidSdk: Int,
    @ColumnInfo(name = "app_version") val appVersion: String
)

@Immutable
@Entity(
    tableName = "benchmark_sub_scores",
    foreignKeys = [
        ForeignKey(
            entity = BenchmarkRun::class,
            parentColumns = ["id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("run_id")]
)
data class BenchmarkSubScore(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "run_id") val runId: Long,
    val name: String,
    val singleScore: Int,
    val multiScore: Int,
    val singleOpsPerSec: Double,
    val multiOpsPerSec: Double,
    val unit: String
)

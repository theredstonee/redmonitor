package com.tamerin.sysmonitor.data.battery

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BatterySampleDao {

    @Insert
    suspend fun insert(sample: BatterySample): Long

    @Query("SELECT * FROM battery_samples WHERE timestamp_ms >= :since ORDER BY timestamp_ms ASC")
    suspend fun samplesSince(since: Long): List<BatterySample>

    @Query("SELECT MAX(timestamp_ms) FROM battery_samples")
    suspend fun lastSampleTimestamp(): Long?

    @Query("SELECT COUNT(*) FROM battery_samples")
    suspend fun sampleCount(): Int

    /** Housekeeping: drop samples older than [olderThanMs]. */
    @Query("DELETE FROM battery_samples WHERE timestamp_ms < :olderThanMs")
    suspend fun purgeOlderThan(olderThanMs: Long)
}

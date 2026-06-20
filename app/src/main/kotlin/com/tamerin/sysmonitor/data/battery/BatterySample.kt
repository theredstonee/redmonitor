package com.tamerin.sysmonitor.data.battery

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "battery_samples",
    indices = [Index("timestamp_ms")]
)
data class BatterySample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
    /** Coulomb counter — total charge IN µAh. Source of truth for delta-based drain calc. */
    @ColumnInfo(name = "charge_counter_uah") val chargeCounterUah: Long,
    /** Reported battery % at sample time. */
    val percent: Float,
    /** Was the screen on when sampled? */
    @ColumnInfo(name = "screen_on") val screenOn: Boolean,
    /** Was the phone charging? Drain analysis only uses NON-charging samples. */
    val charging: Boolean
)

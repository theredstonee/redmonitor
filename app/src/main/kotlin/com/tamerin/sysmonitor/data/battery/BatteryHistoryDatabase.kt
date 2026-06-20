package com.tamerin.sysmonitor.data.battery

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BatterySample::class], version = 1, exportSchema = false)
abstract class BatteryHistoryDatabase : RoomDatabase() {

    abstract fun sampleDao(): BatterySampleDao

    companion object {
        @Volatile
        private var INSTANCE: BatteryHistoryDatabase? = null

        fun get(context: Context): BatteryHistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BatteryHistoryDatabase::class.java,
                    "battery_history.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

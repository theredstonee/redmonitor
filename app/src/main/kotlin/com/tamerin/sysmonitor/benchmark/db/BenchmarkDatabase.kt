package com.tamerin.sysmonitor.benchmark.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [BenchmarkRun::class, BenchmarkSubScore::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(BenchmarkTypeConverters::class)
abstract class BenchmarkDatabase : RoomDatabase() {

    abstract fun benchmarkDao(): BenchmarkDao

    companion object {
        @Volatile
        private var INSTANCE: BenchmarkDatabase? = null

        fun get(context: Context): BenchmarkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BenchmarkDatabase::class.java,
                    "benchmarks.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

class BenchmarkTypeConverters {
    @TypeConverter
    fun benchmarkTypeToString(t: BenchmarkType): String = t.name

    @TypeConverter
    fun stringToBenchmarkType(s: String): BenchmarkType =
        runCatching { BenchmarkType.valueOf(s) }.getOrDefault(BenchmarkType.CPU)
}

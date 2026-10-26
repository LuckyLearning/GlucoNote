package com.gluconote.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gluconote.data.model.GlucoseRecord

@Database(entities = [GlucoseRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao

    companion object {
        const val DATABASE_NAME = "gluconote.db"
    }
}

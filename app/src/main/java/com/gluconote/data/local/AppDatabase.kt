package com.gluconote.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gluconote.data.model.GlucoseRecord

/**
 * 数据库版本演进：
 * - v1：单表 `records`，无索引；
 * - v2：加 3 个查询索引（见 [GlucoseRecord]）。**开发期直接破坏性重建**，
 *   不写 Migration —— 正式发版前必须改成 `addMigrations` + `exportSchema = true`。
 */
@Database(entities = [GlucoseRecord::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao

    companion object {
        const val DATABASE_NAME = "gluconote.db"
    }
}

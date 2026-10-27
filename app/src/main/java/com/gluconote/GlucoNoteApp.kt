package com.gluconote

import android.app.Application
import androidx.room.Room
import com.gluconote.data.local.AppDatabase
import com.gluconote.util.CrashGuard

class GlucoNoteApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            // 开发阶段没有正式数据，改表结构直接重建库，不写 Migration。
            // ⚠️ 发版前必须去掉这一行并补 addMigrations / exportSchema = true，
            //    否则线上用户升级会丢掉全部历史记录。
            .fallbackToDestructiveMigration()
            .build()
    }
}

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
        ).build()
    }
}

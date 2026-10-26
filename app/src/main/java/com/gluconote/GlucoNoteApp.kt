package com.gluconote

import android.app.Application
import androidx.room.Room
import com.gluconote.data.local.AppDatabase

class GlucoNoteApp : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).build()
    }
}

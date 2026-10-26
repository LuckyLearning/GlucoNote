package com.gluconote.data.repository

import com.gluconote.data.local.AppDatabase
import com.gluconote.data.model.GlucoseRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class RecordRepository(private val db: AppDatabase) {

    fun observeAll(): Flow<List<GlucoseRecord>> = db.recordDao().observeAll()

    suspend fun getAll(): List<GlucoseRecord> = withContext(Dispatchers.IO) {
        db.recordDao().getAll()
    }

    suspend fun getBetween(from: Long, to: Long): List<GlucoseRecord> = withContext(Dispatchers.IO) {
        db.recordDao().getBetween(from, to)
    }

    suspend fun insert(record: GlucoseRecord) = withContext(Dispatchers.IO) {
        db.recordDao().insert(record)
    }

    suspend fun update(record: GlucoseRecord) = withContext(Dispatchers.IO) {
        db.recordDao().update(record)
    }

    suspend fun delete(record: GlucoseRecord) = withContext(Dispatchers.IO) {
        db.recordDao().delete(record)
    }

    /**
     * 新增或更新一条记录。传入 [id] 为更新，否则新增。
     */
    suspend fun upsert(
        value: Float,
        period: String,
        measureTime: Long,
        note: String,
        id: String? = null
    ) = withContext(Dispatchers.IO) {
        val record = GlucoseRecord(
            id = id ?: UUID.randomUUID().toString(),
            measureTime = measureTime,
            value = value,
            period = period,
            note = note,
            createdAt = System.currentTimeMillis()
        )
        if (id != null) db.recordDao().update(record) else db.recordDao().insert(record)
    }
}

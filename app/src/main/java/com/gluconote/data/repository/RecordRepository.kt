package com.gluconote.data.repository

import com.gluconote.data.local.AppDatabase
import com.gluconote.data.model.GlucoseRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class RecordRepository(private val db: AppDatabase) {

    fun observeAll(): Flow<List<GlucoseRecord>> = db.recordDao().observeAll()

    fun observeCount(): Flow<Int> = db.recordDao().observeCount()

    suspend fun pageAfter(
        period: String?,
        cursorTime: Long?,
        cursorId: String?,
        limit: Int
    ): List<GlucoseRecord> = withContext(Dispatchers.IO) {
        db.recordDao().pageAfter(period, cursorTime, cursorId, limit)
    }

    suspend fun getAll(): List<GlucoseRecord> = withContext(Dispatchers.IO) {
        db.recordDao().getAll()
    }

    suspend fun getById(id: String): GlucoseRecord? = withContext(Dispatchers.IO) {
        db.recordDao().getById(id)
    }

    suspend fun getBetween(from: Long, to: Long): List<GlucoseRecord> = withContext(Dispatchers.IO) {
        db.recordDao().getBetween(from, to)
    }

    suspend fun insert(record: GlucoseRecord) = withContext(Dispatchers.IO) {
        db.recordDao().insert(record)
    }

    suspend fun insertAll(records: List<GlucoseRecord>) = withContext(Dispatchers.IO) {
        // Batch in chunks so huge seeds stay responsive and Room uses one INSERT list per chunk.
        records.chunked(400).forEach { chunk ->
            db.recordDao().insertAll(chunk)
        }
    }

    suspend fun update(record: GlucoseRecord) = withContext(Dispatchers.IO) {
        db.recordDao().update(record)
    }

    suspend fun delete(record: GlucoseRecord) = withContext(Dispatchers.IO) {
        db.recordDao().delete(record)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.recordDao().clear()
    }

    /**
     * Upsert one record. Pass [id] to update; omit to insert.
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
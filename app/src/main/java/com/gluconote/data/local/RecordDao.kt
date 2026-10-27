package com.gluconote.data.local

import androidx.room.*

import com.gluconote.data.model.GlucoseRecord

import kotlinx.coroutines.flow.Flow

@Dao

interface RecordDao {

    @Query("SELECT * FROM records ORDER BY measureTime DESC")

    fun observeAll(): Flow<List<GlucoseRecord>>

    @Query("SELECT COUNT(*) FROM records")

    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM records ORDER BY measureTime DESC")

    suspend fun getAll(): List<GlucoseRecord>

    @Query("SELECT * FROM records WHERE measureTime BETWEEN :from AND :to ORDER BY measureTime ASC")

    suspend fun getBetween(from: Long, to: Long): List<GlucoseRecord>

    @Query("SELECT * FROM records WHERE id = :id")

    suspend fun getById(id: String): GlucoseRecord?

    /**

     * Keyset page: newest first. Pass null cursors for the first page.

     * [period] null = all tags.

     */

    @Query(

        """

        SELECT * FROM records

        WHERE (:period IS NULL OR period = :period)

          AND (

            :cursorTime IS NULL

            OR measureTime < :cursorTime

            OR (measureTime = :cursorTime AND id < :cursorId)

          )

        ORDER BY measureTime DESC, id DESC

        LIMIT :limit

        """

    )

    suspend fun pageAfter(

        period: String?,

        cursorTime: Long?,

        cursorId: String?,

        limit: Int

    ): List<GlucoseRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insert(record: GlucoseRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<GlucoseRecord>)

    @Update

    suspend fun update(record: GlucoseRecord)

    @Delete

    suspend fun delete(record: GlucoseRecord)

    @Query("DELETE FROM records")

    suspend fun clear()

}


package com.gluconote.data.repository

import androidx.room.invalidationTrackerFlow
import androidx.room.withTransaction
import com.gluconote.data.local.AppDatabase
import com.gluconote.data.local.DailyPoint
import com.gluconote.data.local.HomeAggRow
import com.gluconote.data.local.PeriodRateRow
import com.gluconote.data.local.RangeStatsRow
import com.gluconote.data.local.TrendRow
import com.gluconote.data.model.GlucoseRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 记录仓储层：UI 只通过这里访问数据库。
 *
 * 所有查询都必须是「有索引支撑 + 有限结果集」，不允许把整表读进内存再过滤
 * （20 万行时全表拉取约 286 ms / 每次重组，SQL 聚合只要 16 ms，差 17 倍）。
 */
class RecordRepository(private val db: AppDatabase) {

    private val dao get() = db.recordDao()

    // ---------------------------------------------------------------- 观察

    /**
     * 「records 表被写过」的通知流（增 / 删 / 改都会触发）。
     *
     * 用 Room 的失效追踪器而不是自己写 `SELECT COUNT(*) + MAX(createdAt)` 令牌：
     * 后者在 20 万行时实测 **38 ms/次**（`createdAt` 无索引 → 全表扫描），
     * 比它想替代的查询还贵；`invalidationTrackerFlow` 走 Room 建表时挂的触发器，
     * **零查询开销**，而且天然能感知「只改内容、条数不变」的编辑。
     *
     * 默认不发射初始状态：首屏由各 ViewModel 自己的首次查询负责，避免多查一遍。
     */
    fun observeRecordsChanged(): Flow<Unit> =
        db.invalidationTrackerFlow("records").map { }

    fun observeHomeAgg(startToday: Long, weekAgo: Long): Flow<HomeAggRow> =
        dao.observeHomeAgg(startToday, weekAgo)

    fun observeLatest(): Flow<GlucoseRecord?> = dao.observeLatest()

    fun observeRecent5(): Flow<List<GlucoseRecord>> = dao.observeRecent5()

    // ---------------------------------------------------------------- 分页

    /**
     * Keyset 分页。游标为空 = 取第一页。
     *
     * 内部按「全部 / 指定标签」分流到两条不同 SQL，
     * 保证两种情况都能用上索引前缀（见 [com.gluconote.data.local.RecordDao] 注释）。
     */
    suspend fun page(
        period: String?,
        cursorTime: Long?,
        cursorId: String?,
        limit: Int
    ): List<GlucoseRecord> = withContext(Dispatchers.IO) {
        if (cursorTime == null || cursorId == null) {
            if (period == null) dao.pageAllFirst(limit) else dao.pagePeriodFirst(period, limit)
        } else {
            if (period == null) {
                dao.pageAllAfter(cursorTime, cursorId, limit)
            } else {
                dao.pagePeriodAfter(period, cursorTime, cursorId, limit)
            }
        }
    }

    // ---------------------------------------------------------------- 聚合

    suspend fun rateByPeriod(
        from: Long,
        to: Long,
        period: String?,
        fMin: Float,
        fMax: Float,
        pMin: Float,
        pMax: Float
    ): List<PeriodRateRow> = withContext(Dispatchers.IO) {
        dao.rateByPeriod(from, to, period, fMin, fMax, pMin, pMax)
    }

    suspend fun rangeStats(from: Long, to: Long, period: String?): RangeStatsRow =
        withContext(Dispatchers.IO) { dao.rangeStats(from, to, period) }

    suspend fun countBetween(from: Long, to: Long, period: String?): Int =
        withContext(Dispatchers.IO) { dao.countBetween(from, to, period) }

    suspend fun trendRows(from: Long, to: Long, period: String?): List<TrendRow> =
        withContext(Dispatchers.IO) { dao.trendRows(from, to, period) }

    suspend fun dailyTrend(from: Long, to: Long, period: String?, tzShift: Long): List<DailyPoint> =
        withContext(Dispatchers.IO) { dao.dailyTrend(from, to, period, tzShift) }

    // ---------------------------------------------------------------- 明细

    suspend fun getAll(): List<GlucoseRecord> = withContext(Dispatchers.IO) { dao.getAll() }

    suspend fun getById(id: String): GlucoseRecord? = withContext(Dispatchers.IO) { dao.getById(id) }

    suspend fun getBetweenFiltered(from: Long, to: Long, period: String?): List<GlucoseRecord> =
        withContext(Dispatchers.IO) { dao.getBetweenFiltered(from, to, period) }

    // ---------------------------------------------------------------- 写入

    suspend fun insert(record: GlucoseRecord) = withContext(Dispatchers.IO) {
        dao.insert(record)
    }

    /**
     * 覆盖式批量写入（调试种子数据用）。整批一个事务，避免每行一次 fsync。
     */
    suspend fun insertAll(records: List<GlucoseRecord>) = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        db.withTransaction {
            records.chunked(IMPORT_CHUNK).forEach { dao.insertAll(it) }
        }
        analyze()
    }

    /**
     * 导入 CSV：整批一个事务 + 分块 + `IGNORE`，完成后刷新优化器统计。
     *
     * 为什么必须在同一个事务里：事务外每行都要单独提交并等磁盘落盘，
     * 实测 43.77 ms/行（1000 行 ≈ 44 s）；单事务批量只要 0.045 ms/行，约 981×。
     *
     * @return 实际提交的行数（重复 id 被跳过，所以可能小于 [records] 的 size）
     */
    suspend fun importAll(records: List<GlucoseRecord>): Int = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext 0
        val written = db.withTransaction {
            var n = 0
            records.chunked(IMPORT_CHUNK).forEach { chunk ->
                n += dao.insertIgnore(chunk).count { it != -1L }
            }
            n
        }
        analyze()
        written
    }

    /**
     * 刷新 SQLite 优化器统计信息。
     *
     * 大批量写入后不 `ANALYZE`，优化器可能按旧的基数估算选错索引
     * （尤其 `period` 分布倾斜时），查询计划会从索引查找退化成全表扫描。
     */
    suspend fun analyze() = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.execSQL("ANALYZE")
    }

    suspend fun update(record: GlucoseRecord) = withContext(Dispatchers.IO) {
        dao.update(record)
    }

    suspend fun delete(record: GlucoseRecord) = withContext(Dispatchers.IO) {
        dao.delete(record)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clear()
    }

    /**
     * Upsert one record. Pass [id] to update; omit to insert.
     *
     * 更新分支会刷新 `createdAt`：变更令牌靠它感知「只改内容、不改条数」的编辑。
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
        if (id != null) dao.update(record) else dao.insert(record)
    }

    companion object {
        /**
         * 单条 `INSERT` 语句携带的行数。
         *
         * 400 / 1000 实测几乎一致，2000 反而更慢（SQL 变量数触及上限、
         * 语句编译开销上升）。
         */
        const val IMPORT_CHUNK = 400
    }
}

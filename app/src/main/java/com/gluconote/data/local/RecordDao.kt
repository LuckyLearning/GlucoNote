package com.gluconote.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gluconote.data.model.GlucoseRecord
import kotlinx.coroutines.flow.Flow

/**
 * 记录表数据访问。
 *
 * 设计原则：**任何 UI 路径都不允许 `SELECT *` 全表**。
 * - 列表 → keyset 分页（按需增量，永不整表拉取）；
 * - 概览 / 达标率 / 区间统计 / 趋势 → SQL 聚合（只回传结果行）；
 * - 只有「按天/月/年分桶」留在 Kotlin，且输入窗口被强制限制（见 `StatsViewModel`）；
 * - 需要「表变了就重查」的地方不要写 `SELECT COUNT(*)` 令牌 ——
 *   用 `InvalidationTracker.createFlow("records")`，Room 内部靠表的写触发器通知，零查询开销。
 */
@Dao
interface RecordDao {

    // ---------------------------------------------------------------- 首页

    /**
     * 首页概览：一次查询同时得到今日条数与近 7 天均值。
     *
     * 聚合无 `GROUP BY` 时必然返回 1 行（空表时 `todayCount = 0`、`weekAvg = NULL`），
     * 所以 Kotlin 侧可以直接当非空行处理。
     */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN measureTime >= :startToday THEN 1 ELSE 0 END), 0) AS todayCount,
               AVG(CASE WHEN measureTime >= :weekAgo THEN value END) AS weekAvg
        FROM records
        WHERE measureTime >= :weekAgo
        """
    )
    fun observeHomeAgg(startToday: Long, weekAgo: Long): Flow<HomeAggRow>

    @Query("SELECT * FROM records ORDER BY measureTime DESC, id DESC LIMIT 1")
    fun observeLatest(): Flow<GlucoseRecord?>

    @Query("SELECT * FROM records ORDER BY measureTime DESC, id DESC LIMIT 5")
    fun observeRecent5(): Flow<List<GlucoseRecord>>

    // ---------------------------------------------------------------- 分页（keyset）

    @Query("SELECT * FROM records ORDER BY measureTime DESC, id DESC LIMIT :limit")
    suspend fun pageAllFirst(limit: Int): List<GlucoseRecord>

    @Query(
        """
        SELECT * FROM records
        WHERE measureTime < :cursorTime OR (measureTime = :cursorTime AND id < :cursorId)
        ORDER BY measureTime DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun pageAllAfter(cursorTime: Long, cursorId: String, limit: Int): List<GlucoseRecord>

    @Query(
        """
        SELECT * FROM records
        WHERE period = :period
        ORDER BY measureTime DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun pagePeriodFirst(period: String, limit: Int): List<GlucoseRecord>

    /**
     * 按标签筛选的续页。
     *
     * 与旧版 `(:period IS NULL OR period = :period)` 的区别：那条 SQL 让优化器
     * 无法使用 `period` 前缀索引，只能整表倒序扫描。这里把「全部 / 指定标签」
     * 拆成两条语句，确保走 `idx_period_measure_time_id`。
     */
    @Query(
        """
        SELECT * FROM records
        WHERE period = :period
          AND (measureTime < :cursorTime OR (measureTime = :cursorTime AND id < :cursorId))
        ORDER BY measureTime DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun pagePeriodAfter(
        period: String,
        cursorTime: Long,
        cursorId: String,
        limit: Int
    ): List<GlucoseRecord>

    // ---------------------------------------------------------------- 聚合

    @Query(
        """
        SELECT period,
               COUNT(*) AS total,
               SUM(
                 CASE
                   WHEN period = 'fasting' AND value BETWEEN :fMin AND :fMax THEN 1
                   WHEN period <> 'fasting' AND value BETWEEN :pMin AND :pMax THEN 1
                   ELSE 0
                 END
               ) AS inRange
        FROM records
        WHERE measureTime BETWEEN :from AND :to
          AND (:period IS NULL OR period = :period)
        GROUP BY period
        """
    )
    suspend fun rateByPeriod(
        from: Long,
        to: Long,
        period: String?,
        fMin: Float,
        fMax: Float,
        pMin: Float,
        pMax: Float
    ): List<PeriodRateRow>

    @Query(
        """
        SELECT COUNT(*) AS count,
               AVG(value) AS valueAvg,
               MAX(value) AS valueMax,
               MIN(value) AS valueMin,
               AVG(value * value) AS valueAvgSq
        FROM records
        WHERE measureTime BETWEEN :from AND :to
          AND (:period IS NULL OR period = :period)
        """
    )
    suspend fun rangeStats(from: Long, to: Long, period: String?): RangeStatsRow

    @Query(
        """
        SELECT COUNT(*) FROM records
        WHERE measureTime BETWEEN :from AND :to
          AND (:period IS NULL OR period = :period)
        """
    )
    suspend fun countBetween(from: Long, to: Long, period: String?): Int

    // ---------------------------------------------------------------- 趋势

    /** 只取三列 → 命中覆盖索引 `idx_cover_time_value_period`，完全不回表 */
    @Query(
        """
        SELECT measureTime, value, period FROM records
        WHERE measureTime BETWEEN :from AND :to
          AND (:period IS NULL OR period = :period)
        ORDER BY measureTime ASC
        """
    )
    suspend fun trendRows(from: Long, to: Long, period: String?): List<TrendRow>

    /** 点数超过阈值时的降采样：按本地自然日聚合 */
    @Query(
        """
        SELECT (measureTime + :tzShift) / 86400000 AS day,
               MIN(measureTime) AS firstTs,
               AVG(value) AS valueAvg,
               MAX(value) AS valueMax,
               MIN(value) AS valueMin,
               COUNT(*) AS count
        FROM records
        WHERE measureTime BETWEEN :from AND :to
          AND (:period IS NULL OR period = :period)
        GROUP BY day
        ORDER BY day
        """
    )
    suspend fun dailyTrend(from: Long, to: Long, period: String?, tzShift: Long): List<DailyPoint>

    // ---------------------------------------------------------------- 明细读取

    @Query("SELECT * FROM records ORDER BY measureTime DESC")
    suspend fun getAll(): List<GlucoseRecord>

    /** 分桶统计的输入（窗口已由调用方限死） */
    @Query(
        """
        SELECT * FROM records
        WHERE measureTime BETWEEN :from AND :to
          AND (:period IS NULL OR period = :period)
        ORDER BY measureTime ASC
        """
    )
    suspend fun getBetweenFiltered(from: Long, to: Long, period: String?): List<GlucoseRecord>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getById(id: String): GlucoseRecord?

    // ---------------------------------------------------------------- 写入

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: GlucoseRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<GlucoseRecord>)

    /**
     * 导入专用：`IGNORE` 遇到重复 id 跳过。
     *
     * `REPLACE` 在 SQLite 里是 **DELETE + INSERT**，会让 3 个索引的条目反复删除重建；
     * 导入时用 `IGNORE` 既省一半索引维护，也不会覆盖用户后来编辑过的数据。
     *
     * 返回每条语句的 rowId：被 `IGNORE` 跳过的行返回 -1，可据此统计真实写入条数。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(records: List<GlucoseRecord>): List<Long>

    @Update
    suspend fun update(record: GlucoseRecord)

    @Delete
    suspend fun delete(record: GlucoseRecord)

    @Query("DELETE FROM records")
    suspend fun clear()
}

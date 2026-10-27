package com.gluconote.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条血糖测量记录。
 *
 * 三个索引各有明确归属（20 万行实测依据见 `docs/数据层优化方案.md` §2）：
 * - `idx_measure_time_id`：记录页 keyset 分页 + `ORDER BY measureTime DESC` + 时间窗聚合；
 * - `idx_period_measure_time_id`：按标签筛选的分页与时间窗查询；
 * - `idx_cover_time_value_period`：`value` 覆盖索引，聚合/趋势查询只走索引、不回表。
 *
 * 注意：索引会让批量导入慢约 90%（687 ms → 1305 ms / 20 万行），
 * 但读路径收益是数千倍，且导入是一次性动作 —— 不要为导入速度删索引。
 */
@Entity(
    tableName = "records",
    indices = [
        Index(value = ["measureTime", "id"], name = "idx_measure_time_id"),
        Index(value = ["period", "measureTime", "id"], name = "idx_period_measure_time_id"),
        Index(value = ["measureTime", "value", "period"], name = "idx_cover_time_value_period")
    ]
)
data class GlucoseRecord(
    @PrimaryKey val id: String,
    /** 测量时间，epoch 毫秒 */
    val measureTime: Long,
    /** 血糖值 mmol/L */
    val value: Float,
    /** 标签 key，对应 [Period.key] */
    val period: String,
    val note: String,
    val createdAt: Long = System.currentTimeMillis()
)

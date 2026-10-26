package com.gluconote.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "records")
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

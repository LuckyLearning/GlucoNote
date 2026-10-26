package com.gluconote.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val DAY_MS: Long = 24L * 60 * 60 * 1000

fun startOfDay(ts: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

fun formatDate(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))

fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

fun formatDateTime(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))

fun formatMonth(ts: Long): String =
    SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(ts))

fun formatYear(ts: Long): String =
    SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(ts))

package com.gluconote.util

import com.gluconote.data.model.GlucoseRecord
import com.gluconote.data.model.Period
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val CSV_SDF = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

/** 导出为带 BOM 的 CSV（Excel 友好），含时间/数值/标签/备注 */
fun exportCsv(records: List<GlucoseRecord>): String {
    val sb = StringBuilder()
    sb.append('\uFEFF') // BOM
    sb.appendLine("id,measure_time,value,period,period_label,note")
    records.sortedBy { it.measureTime }.forEach { r ->
        val cols = listOf(
            r.id,
            CSV_SDF.format(Date(r.measureTime)),
            r.value.toString(),
            r.period,
            Period.fromKey(r.period).plainLabel(),
            r.note
        )
        sb.appendLine(cols.joinToString(",") { escapeCsv(it) })
    }
    return sb.toString()
}

/** 解析 CSV（兼容 BOM 与引号转义），返回记录列表 */
fun parseCsv(text: String): List<GlucoseRecord> {
    val lines = text.replace("\uFEFF", "")
        .lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .toList()
    if (lines.size <= 1) return emptyList()
    val out = mutableListOf<GlucoseRecord>()
    for (i in 1 until lines.size) {
        val cols = parseLine(lines[i])
        if (cols.size < 5) continue
        try {
            val time = CSV_SDF.parse(cols[1])?.time ?: continue
            val value = cols[2].toFloatOrNull() ?: continue
            val period = cols[3]
            val note = cols.getOrNull(5) ?: ""
            out.add(
                GlucoseRecord(
                    id = cols[0].ifBlank { UUID.randomUUID().toString() },
                    measureTime = time,
                    value = value,
                    period = period,
                    note = note
                )
            )
        } catch (_: Exception) {
            // 跳过坏行
        }
    }
    return out
}

private fun escapeCsv(s: String): String =
    if (s.contains(',') || s.contains('"') || s.contains('\n')) {
        "\"" + s.replace("\"", "\"\"") + "\""
    } else s

private fun parseLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val cur = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            inQuotes -> {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"'); i += 2; continue
                    }
                    inQuotes = false; i++
                } else {
                    cur.append(c); i++
                }
            }
            c == '"' -> { inQuotes = true; i++ }
            c == ',' -> { result.add(cur.toString()); cur.setLength(0); i++ }
            else -> { cur.append(c); i++ }
        }
    }
    result.add(cur.toString())
    return result
}

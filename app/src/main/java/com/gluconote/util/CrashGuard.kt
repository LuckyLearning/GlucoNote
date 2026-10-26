package com.gluconote.util

import android.app.Application
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃捕获：把未捕获异常落到 `filesDir/crash/last_crash.txt`（只留最近一次），
 * 「我的 → 诊断」页可查看/导出。解决"用户说闪退但拿不到日志"的定位难题。
 *
 * 捕获后仍交给系统默认处理器（照常崩溃退出），这里只做留痕。
 */
object CrashGuard {

    private const val DIR = "crash"
    private const val FILE = "last_crash.txt"

    fun install(app: Application) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val dir = File(app.filesDir, DIR).apply { mkdirs() }
                File(dir, FILE).writeText(
                    buildString {
                        appendLine("time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                        appendLine("app: ${app.packageName}")
                        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("thread: ${thread.name}")
                        appendLine()
                        appendLine("stacktrace:")
                        appendLine(e.stackTraceToString())
                        e.cause?.let {
                            appendLine()
                            appendLine("cause:")
                            appendLine(it.stackTraceToString())
                        }
                    }
                )
            }
            prev?.uncaughtException(thread, e)
        }
    }

    /** 最近的崩溃日志；没有则返回 null */
    fun lastCrashFile(app: Application): File? =
        File(app.filesDir, "$DIR/$FILE").takeIf { it.exists() && it.length() > 0 }

    fun clear(app: Application) {
        File(app.filesDir, "$DIR/$FILE").delete()
    }
}

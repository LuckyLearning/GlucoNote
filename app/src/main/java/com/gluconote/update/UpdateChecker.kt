package com.gluconote.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.gluconote.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** 公共分发仓库中最新版本的信息 */
data class UpdateInfo(
    /** 版本号（已去掉 tag 的 v 前缀），如 1.0.1 */
    val version: String,
    /** 原始 tag 名，如 v1.0.1 */
    val tagName: String,
    /** 更新说明（纯文本，可直接展示） */
    val notes: String,
    /** APK 下载直链（公共仓库原始地址，实际下载会再经过线路变换） */
    val apkUrl: String,
    /** APK 文件名，如 GlucoNote-v1.0.1.apk */
    val apkName: String,
    /** APK 字节数，0 表示未知 */
    val apkSizeBytes: Long,
    /** 发布时间（ISO 字符串） */
    val publishedAt: String,
    /** 该版本在公共仓库里的目录地址（浏览器可打开） */
    val releaseUrl: String,
    /** APK 的 SHA-256（小写十六进制），空串表示清单里没给 */
    val apkSha256: String = "",
)

sealed interface UpdateCheckResult {
    /** 有新版本可用 */
    data class Available(val info: UpdateInfo, val route: String = "") : UpdateCheckResult

    /** 已是最新 */
    data class UpToDate(val latestVersion: String, val route: String = "") : UpdateCheckResult

    /** 检查失败（离线 / 未发布等），[reason] 可直接展示给用户 */
    data class Failed(val reason: String) : UpdateCheckResult
}

/**
 * 应用内更新。
 *
 * 版本信息与安装包都来自**公共分发仓库** `LuckyLearning/Release` 的静态文件：
 * ```
 * https://raw.githubusercontent.com/LuckyLearning/Release/main/GlucoNote/latest.json
 * https://raw.githubusercontent.com/LuckyLearning/Release/main/GlucoNote/v1.0.1/GlucoNote-v1.0.1.apk
 * ```
 * `latest.json` 由发布流水线（scripts/publish-release-repo.sh）生成，带每个包的大小与
 * SHA-256，下载后做「大小 + 摘要」双校验。
 *
 * 国内直连 `raw.githubusercontent.com` 基本不通，所以维护了一张**多线路表**：
 * 直连、GitHub 加速代理（路径前缀式）、jsDelivr / Statically（CDN 镜像）。
 *
 * 策略：
 * - **检查更新 → 并发竞速**，全部线路同时发请求，谁先返回合法清单就用谁，
 *   不需要用户知道哪条线通。命中的线路记在内存里，本次进程内下载优先走它。
 * - **下载 APK → 顺序重试**，从命中线路开始，失败换下一条（1.5MB × N 并发太浪费流量）。
 * - 任一线路拿到的字节都要过「APK 头 + 大小 + SHA-256」三重校验，镜像不老实也装不上。
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"

    private val appBase: String
        get() = "https://raw.githubusercontent.com/${BuildConfig.RELEASE_OWNER}/${BuildConfig.RELEASE_REPO}/${BuildConfig.RELEASE_BRANCH}/${BuildConfig.RELEASE_APP_DIR}"

    private val manifestUrl: String get() = "$appBase/latest.json"

    /** 当前安装版本（来自 BuildConfig，与 build.gradle.kts 的 versionName 一致） */
    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** 公共仓库里本软件的目录页面（检查失败时可引导用户手动打开） */
    fun releasePageUrl(): String = BuildConfig.RELEASE_PAGE_URL

    // ------------------------------------------------------------ 线路

    /**
     * 一条候选线路：把公共仓库的原始 URL 变换成该线路可访问的 URL。
     *
     * [tier] 线路层级，决定**检查更新**的先后：
     * - 0 = 直连 / GitHub 加速代理：不缓存，清单一定是最新的
     * - 1 = jsDelivr / Statically 等 CDN：会缓存分支文件（可达数天），
     *       若先返回旧清单会误报「已是最新」，所以只在 tier 0 全不通时才用
     *
     * [manifestOnly] = 只用来取 `latest.json`，不参与 APK 下载
     * （实测 Statically 取小文件正常，取 APK 会挂住不出字节）。
     * 注意 APK 走的是 `vX.Y.Z/` 这种版本固定路径，CDN 缓存是安全的，
     * 所以下载不受 tier 限制。
     */
    internal class Route(
        val name: String,
        val tier: Int = 0,
        val manifestOnly: Boolean = false,
        private val builder: (String) -> String?,
    ) {
        fun resolve(rawUrl: String): String? = builder(rawUrl)
    }

    /**
     * 线路表（顺序即默认优先级）。只收**实测活着**的镜像；
     * 加新线路前请先用 curl 确认该域名下 `/<原始URL>` 能拿到内容，别凭印象加。
     */
    private val ALL_ROUTES: List<Route> = listOf(
        Route("GitHub 直连") { raw -> raw.takeIf { it.startsWith("https://", true) } },
        Route("ghfast") { raw -> proxy("https://ghfast.top/", raw) },
        Route("gh-proxy") { raw -> proxy("https://gh-proxy.com/", raw) },
        Route("ghproxy.net") { raw -> proxy("https://ghproxy.net/", raw) },
        Route("gh.llkk.cc") { raw -> proxy("https://gh.llkk.cc/", raw) },
        Route("jsDelivr", tier = 1) { raw -> jsdelivr("fastly.jsdelivr.net", raw) },
        Route("jsDelivr CDN", tier = 1) { raw -> jsdelivr("cdn.jsdelivr.net", raw) },
        Route("jsDelivr Gcore", tier = 1) { raw -> jsdelivr("gcore.jsdelivr.net", raw) },
        Route("Statically", tier = 1, manifestOnly = true) { raw -> statically(raw) },
    )

    /** 本次进程中检查/下载成功的线路名，下载时优先复用 */
    @Volatile
    private var goodRouteName: String? = null

    /** tier 0（直连 + 代理）的竞速预算 */
    private const val PRIMARY_BUDGET_MS = 7_000L

    /** tier 1（CDN 兜底）的竞速预算 */
    private const val CDN_BUDGET_MS = 8_000L

    /**
     * 生成某个原始 URL 的候选线路（命中过的线路排最前，其余保持默认顺序）。
     * [maxTier] 限制可用的线路层级；[forDownload] 时排除仅清单线路。
     */
    internal fun candidatesFor(
        rawUrl: String,
        forDownload: Boolean = false,
        maxTier: Int = 1,
    ): List<Pair<String, String>> {
        val good = goodRouteName
        val pool = ALL_ROUTES.filter { it.tier <= maxTier && !(forDownload && it.manifestOnly) }
        val ordered = if (good == null) pool else pool.sortedBy { if (it.name == good) 0 else 1 }
        return ordered
            .mapNotNull { r -> r.resolve(rawUrl)?.let { r.name to it } }
            .distinctBy { it.second }
    }

    /**
     * 路径前缀式加速代理只对 GitHub 地址有意义。
     * 这里限制为 GitHub 域，将来若接入非 GitHub 源（如 Gitee）不会被误套上。
     */
    private fun proxy(prefix: String, raw: String): String? {
        if (!raw.startsWith("https://", true)) return null
        val isGithub = raw.contains("raw.githubusercontent.com/", true) ||
            raw.contains("github.com/", true)
        return if (isGithub) prefix + raw else null
    }

    /** raw.githubusercontent.com/<owner>/<repo>/<branch>/<path...> 拆成四段 */
    private fun ghParts(raw: String): List<String>? {
        val prefix = "https://raw.githubusercontent.com/"
        if (!raw.startsWith(prefix, ignoreCase = true)) return null
        val parts = raw.substring(prefix.length).split('/', limit = 4)
        return if (parts.size == 4) parts else null
    }

    private fun jsdelivr(host: String, raw: String): String? {
        val p = ghParts(raw) ?: return null
        return "https://$host/gh/${p[0]}/${p[1]}@${p[2]}/${p[3]}"
    }

    private fun statically(raw: String): String? {
        val p = ghParts(raw) ?: return null
        return "https://cdn.statically.io/gh/${p[0]}/${p[1]}/${p[2]}/${p[3]}"
    }

    // ------------------------------------------------------------ 查询

    /**
     * 查询最新版本。
     *
     * 分两级竞速，避免 CDN 缓存把旧清单当成最新：
     * 1. tier 0（直连 + 加速代理）同时发请求，谁先给出合法清单就用谁；
     * 2. tier 0 全不通（典型国内无 VPN 且代理都挂）才启用 tier 1（jsDelivr / Statically）。
     *
     * 永不抛异常，失败一律折成 [UpdateCheckResult.Failed]。
     */
    suspend fun checkLatest(current: String = currentVersion): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            if (candidatesFor(manifestUrl).isEmpty()) {
                return@withContext UpdateCheckResult.Failed("没有可用的更新线路")
            }
            var lastFailure = UpdateCheckResult.Failed("没有可用的更新线路")
            for (tier in 0..1) {
                val candidates = candidatesFor(manifestUrl, maxTier = tier)
                if (candidates.isEmpty()) continue
                val budget = if (tier == 0) PRIMARY_BUDGET_MS else CDN_BUDGET_MS
                val result = raceManifest(candidates, current, budget)
                if (result !is UpdateCheckResult.Failed) return@withContext result
                lastFailure = result
            }
            lastFailure
        }

    /** 一组线路并发竞速，返回第一个成功的结论；全失败返回 [UpdateCheckResult.Failed] */
    private suspend fun raceManifest(
        candidates: List<Pair<String, String>>,
        current: String,
        budgetMs: Long,
    ): UpdateCheckResult = coroutineScope {
        val winner = CompletableDeferred<UpdateCheckResult>()
        val lastCode = AtomicInteger(0)
        val lastError = AtomicReference<Throwable?>(null)

        val jobs = candidates.map { (routeName, url) ->
            launch {
                try {
                    val (code, body) = httpGet(url)
                    if (code == 200) {
                        when (val parsed = parseManifest(body, current)) {
                            is UpdateCheckResult.Available -> if (winner.complete(parsed.copy(route = routeName))) {
                                goodRouteName = routeName
                            }
                            is UpdateCheckResult.UpToDate -> if (winner.complete(parsed.copy(route = routeName))) {
                                goodRouteName = routeName
                            }
                            // 清单不合法：这条线路可能返回了错误页，继续等别的线路
                            is UpdateCheckResult.Failed -> Unit
                        }
                    } else {
                        lastCode.set(code)
                        if (verbose) Log.w(TAG, "线路 $routeName 返回 HTTP $code")
                    }
                } catch (t: Throwable) {
                    lastError.set(t)
                    if (verbose) Log.w(TAG, "线路 $routeName 失败", t)
                }
            }
        }
        // 全部线路都失败时立刻给结论，别让调用方干等预算用尽
        launch {
            jobs.forEach { it.join() }
            winner.complete(failureOf(lastCode.get(), lastError.get()))
        }
        val result = withTimeoutOrNull(budgetMs) { winner.await() }
            ?: failureOf(lastCode.get(), lastError.get())
        jobs.forEach { it.cancel() }
        result
    }

    private fun failureOf(code: Int, error: Throwable?): UpdateCheckResult.Failed = when {
        code == 404 -> UpdateCheckResult.Failed("公共仓库还没有发布版本")
        code != 0 -> UpdateCheckResult.Failed("检查更新失败（HTTP $code）")
        else -> UpdateCheckResult.Failed(describe(error ?: IOException("网络异常")))
    }

    /**
     * 解析 `latest.json` 并做出结论。纯函数、可单测。
     * 新字段必须可选 —— 旧端不能被新清单弄崩。
     */
    internal fun parseManifest(body: String, current: String): UpdateCheckResult {
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: return UpdateCheckResult.Failed("版本清单格式不正确")
        val version = json.optString("version").trim().removePrefix("v").removePrefix("V")
        if (version.isBlank()) return UpdateCheckResult.Failed("版本清单里没有版本号")
        if (!isNewer(version, current)) return UpdateCheckResult.UpToDate(version)

        val tag = json.optString("tag").ifBlank { "v$version" }
        val versionDir = "$appBase/$tag"
        val assets = json.optJSONArray("assets")
        var file: String? = null
        var size = 0L
        var sha = ""
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val o = assets.optJSONObject(i) ?: continue
                val f = o.optString("file").trim()
                if (f.isBlank()) continue
                // 无 ABI 分包：取第一个（或 universal）条目
                if (file == null || o.optString("abi") == "universal") {
                    file = f; size = o.optLong("size", 0L); sha = o.optString("sha256").trim()
                    if (o.optString("abi") != "universal") break
                }
            }
        }
        if (file == null) return UpdateCheckResult.Failed("新版本没有附带 APK 安装包")

        return UpdateCheckResult.Available(
            UpdateInfo(
                version = version,
                tagName = tag,
                notes = json.optString("notes"),
                apkUrl = "$versionDir/$file",
                apkName = file,
                apkSizeBytes = size,
                publishedAt = json.optString("publishedAt"),
                releaseUrl = "https://github.com/${BuildConfig.RELEASE_OWNER}/${BuildConfig.RELEASE_REPO}/tree/${BuildConfig.RELEASE_BRANCH}/${BuildConfig.RELEASE_APP_DIR}/$tag",
                apkSha256 = sha,
            )
        )
    }

    // ------------------------------------------------------------ 下载

    /**
     * 下载 APK 到 `cacheDir/updates/`。先写 `.part`，完整性校验通过后才改名。
     * 线路按「检查时命中的优先，其余兜底」顺序重试，全部失败时抛最后一次的错误，
     * 由界面引导用户浏览器手动下载。
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onRoute: (String) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR_NAME).apply { if (!exists()) mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }

        val safeName = info.apkName
            .ifBlank { "GlucoNote-v${info.version}.apk" }
            .replace(Regex("[^A-Za-z0-9._\\-]"), "_")
        val target = File(dir, safeName)
        val part = File(dir, "$safeName.part")

        var lastError: Throwable? = null
        for ((routeName, url) in candidatesFor(info.apkUrl, forDownload = true)) {
            try {
                onRoute(routeName)
                val responseTotal = downloadOnce(url, part, onProgress)
                verifyApk(part, info.apkSizeBytes, responseTotal, info.apkSha256)
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                goodRouteName = routeName
                return@withContext target
            } catch (t: Throwable) {
                lastError = t
                runCatching { part.delete() }
                if (verbose) Log.w(TAG, "下载线路失败，切换下一条: $routeName", t)
            }
        }
        throw lastError ?: IOException("所有下载线路均失败")
    }

    /** 下载单个候选线路到 [part]，返回响应声明的总字节数（0 = 未知） */
    private fun downloadOnce(
        url: String,
        part: File,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 25_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "GlucoNote-Android/$currentVersion")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")

            val total = conn.contentLengthLong.takeIf { it > 0 } ?: 0L
            var downloaded = 0L
            part.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var n = input.read(buf)
                    while (n > 0) {
                        out.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                        n = input.read(buf)
                    }
                    out.flush()
                }
            }
            return total
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 校验下载到的文件：APK 头（zip 的 `PK`）→ 大小比对 → SHA-256 逐字节比。
     */
    private fun verifyApk(file: File, expectedSize: Long, responseSize: Long, expectedSha256: String) {
        if (!file.exists() || file.length() < 100 * 1024) {
            throw IOException("下载的安装包不完整")
        }
        val head = ByteArray(4)
        file.inputStream().use { it.read(head) }
        if (!(head[0] == 0x50.toByte() && head[1] == 0x4B.toByte())) {
            throw IOException("下载的文件不是有效的 APK")
        }
        val basis = when {
            expectedSize > 0 -> expectedSize
            responseSize > 0 -> responseSize
            else -> return
        }
        if (file.length() != basis) {
            throw IOException("安装包大小不符（${file.length()} / $basis），请重新下载")
        }
        if (expectedSha256.isNotBlank()) {
            val actual = sha256Of(file)
            if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                throw IOException("安装包校验失败（SHA-256 不一致），请换网络重试")
            }
        }
    }

    /** 文件 SHA-256（小写十六进制） */
    internal fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ------------------------------------------------------------ 安装

    /** Android 8.0+ 需要用户先允许「安装未知应用」 */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }

    /** 交给系统安装器。失败返回带原因的 [Result] */
    fun install(context: Context, apk: File): Result<Unit> = runCatching {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = android.content.ClipData.newRawUri("apk", uri)
        }
        context.startActivity(intent)
    }

    /** 跳转到「安装未知应用」授权页 */
    fun unknownSourcesIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            null
        }

    /** 用系统浏览器打开发布页（应用内全部线路都失败时的兜底出口） */
    fun openReleasePage(context: Context): Result<Unit> = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(releasePageUrl()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ------------------------------------------------------------ 工具

    private fun httpGet(url: String): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "GlucoNote-Android/$currentVersion")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to body
        } finally {
            conn.disconnect()
        }
    }

    /** 语义化版本比较：1.10.0 > 1.9.2。非数字段按 0 处理 */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parse(raw: String): List<Int> = raw
            .substringBefore('-').substringBefore('+')
            .split('.').map { it.trim().toIntOrNull() ?: 0 }
        val a = parse(candidate)
        val b = parse(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun describe(t: Throwable): String = when (t) {
        is java.net.UnknownHostException -> "无法解析更新服务器地址，请检查网络后重试"
        is java.net.SocketTimeoutException ->
            "连接更新服务器超时，请稍后重试，或在浏览器中打开发布页手动下载"
        is javax.net.ssl.SSLException -> "安全连接失败，请检查网络环境"
        is IOException -> "网络异常：${t.message ?: "请稍后重试"}"
        else -> "检查更新失败：${t.message ?: t.javaClass.simpleName}"
    }

    /** 字节数格式化为 MB，未知大小时返回空串 */
    fun formatSize(bytes: Long): String =
        if (bytes <= 0) "" else "%.2f MB".format(bytes / 1024.0 / 1024.0)

    private const val DIR_NAME = "updates"

    /** 调试日志开关（Release 默认关闭） */
    internal var verbose = false
}

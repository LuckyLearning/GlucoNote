package com.gluconote.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新线路映射与版本比较的纯逻辑测试（不联网）。
 *
 * 注意：`parseManifest` 依赖 `org.json`，而单测里的 android.jar 是空壳、
 * 本环境又取不到 Maven 上的 org.json 实现，所以清单解析没有单测覆盖，
 * 只能靠真机自测（检查更新 → 是否有新版本 → 下载 → 校验）。
 */
class UpdateCheckerTest {

    private val manifest =
        "https://raw.githubusercontent.com/LuckyLearning/Release/main/GlucoNote/latest.json"

    @Test
    fun candidates_覆盖多线路且不重复() {
        val list = UpdateChecker.candidatesFor(manifest)
        // 国内直连基本不通，必须有多条备用线路
        assertTrue("线路数应 >= 6，实际 ${list.size}", list.size >= 6)
        assertEquals("线路 URL 不应重复", list.size, list.map { it.second }.toSet().size)
        assertTrue(list.all { it.second.startsWith("https://") })
        assertTrue(list.all { it.first.isNotBlank() })

        // 直连必须保留（海外网络最快）
        assertTrue(list.any { it.second == manifest })
        // 三种变换都要有：路径前缀代理 / jsDelivr / Statically
        assertTrue(list.any { it.second == "https://ghfast.top/$manifest" })
        assertTrue(
            list.any {
                it.second == "https://fastly.jsdelivr.net/gh/LuckyLearning/Release@main/GlucoNote/latest.json"
            }
        )
        assertTrue(
            list.any {
                it.second == "https://cdn.statically.io/gh/LuckyLearning/Release/main/GlucoNote/latest.json"
            }
        )
    }

    @Test
    fun candidates_一级线路不含CDN() {
        val t0 = UpdateChecker.candidatesFor(manifest, maxTier = 0)
        // CDN 会缓存分支上的 latest.json，可能滞后 —— 第一轮不能用
        assertTrue(t0.none { it.second.contains("jsdelivr") || it.second.contains("statically") })
        assertTrue(t0.any { it.second == manifest })
        assertTrue(t0.any { it.second == "https://ghfast.top/$manifest" })
    }

    @Test
    fun candidates_非GitHub地址不套代理() {
        val other = "https://example.com/app/latest.json"
        val list = UpdateChecker.candidatesFor(other)
        // jsDelivr / Statically / 路径前缀代理都只对 GitHub 地址有意义
        assertEquals(1, list.size)
        assertEquals(other, list.first().second)
    }

    @Test
    fun candidates_APK地址同样能走镜像() {
        val apk =
            "https://raw.githubusercontent.com/LuckyLearning/Release/main/GlucoNote/v1.2.0/GlucoNote-v1.2.0.apk"
        val list = UpdateChecker.candidatesFor(apk)
        assertTrue(list.size >= 6)
        assertTrue(
            list.any {
                it.second ==
                    "https://fastly.jsdelivr.net/gh/LuckyLearning/Release@main/GlucoNote/v1.2.0/GlucoNote-v1.2.0.apk"
            }
        )
    }

    @Test
    fun candidates_下载线路排除仅清单线路() {
        val apk =
            "https://raw.githubusercontent.com/LuckyLearning/Release/main/GlucoNote/v1.2.0/GlucoNote-v1.2.0.apk"
        val check = UpdateChecker.candidatesFor(apk)
        val download = UpdateChecker.candidatesFor(apk, forDownload = true)
        // Statically 取 APK 会挂住，不能出现在下载线路里
        assertTrue(check.any { it.first == "Statically" })
        assertTrue(download.none { it.first == "Statically" })
        assertTrue(download.size >= 5)
    }

    @Test
    fun isNewer_语义版本比较() {
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.99.99"))
        assertTrue(!UpdateChecker.isNewer("1.1.4", "1.1.4"))
        assertTrue(!UpdateChecker.isNewer("1.1.3", "1.1.4"))
    }
}

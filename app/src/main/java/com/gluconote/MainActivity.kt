package com.gluconote

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.gluconote.data.model.ThemeMode
import com.gluconote.data.repository.RecordRepository
import com.gluconote.data.repository.SettingsRepository
import com.gluconote.debug.DebugSeedData
import com.gluconote.ui.main.MainScreen
import com.gluconote.ui.theme.GlucoNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // lifecycle-runtime-compose 2.8+ 把 LocalLifecycleOwner 移出了 compose.ui，
            // 这里显式提供，避免 collectAsStateWithLifecycle 启动即崩。
            CompositionLocalProvider(LocalLifecycleOwner provides this@MainActivity) {
                val settingsRepo = remember { SettingsRepository(applicationContext) }
                val themeKey by remember {
                    settingsRepo.settingsFlow.map { it.themeMode }
                }.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM.key)

                val dark = when (ThemeMode.fromKey(themeKey)) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }

                val view = LocalView.current
                SideEffect {
                    // 系统栏图标明暗必须跟应用主题走：
                    // 用户强制浅色而系统是深色时，默认行为会得到「浅底 + 浅图标」看不见。
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                    // 窗口底色同步应用主题：values-night 只跟随系统，
                    // 强制深色时冷启动那一帧否则仍是浅色窗口底（闪白）。
                    window.setBackgroundDrawable(ColorDrawable(if (dark) DARK_BG else LIGHT_BG))
                }

                GlucoNoteTheme(darkTheme = dark) {
                    MainScreen()
                }
            }
        }

        maybeHandleDebugSeed(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeHandleDebugSeed(intent)
    }

    /** Debug-only: adb shell am start -n com.gluconote/.MainActivity --ez seed_debug true */
    private fun maybeHandleDebugSeed(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        val seedDebug = intent?.getBooleanExtra(EXTRA_SEED_DEBUG, false) == true
        val seedLarge = intent?.getBooleanExtra(EXTRA_SEED_LARGE, false) == true
        if (!seedDebug && !seedLarge) return
        intent?.removeExtra(EXTRA_SEED_DEBUG)
        intent?.removeExtra(EXTRA_SEED_LARGE)

        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                val app = application as GlucoNoteApp
                val repo = RecordRepository(app.database)
                val settingsRepo = SettingsRepository(app)
                val records = if (seedLarge) DebugSeedData.buildLargeRecords() else DebugSeedData.buildRecords()
                repo.clear()
                repo.insertAll(records)
                settingsRepo.save(DebugSeedData.demoSettings)
                records.size
            }
            val label = if (seedLarge) "大数据" else "调试数据"
            Toast.makeText(this@MainActivity, "已加载$label：$count 条", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_SEED_DEBUG = "seed_debug"
        const val EXTRA_SEED_LARGE = "seed_large"

        // 必须与 ui/theme/Theme.kt 的 background、res/values(-night)/colors.xml 保持一致
        private const val LIGHT_BG = 0xFFF6FAF6.toInt()
        private const val DARK_BG = 0xFF0F1511.toInt()
    }
}

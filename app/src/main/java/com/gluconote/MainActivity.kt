package com.gluconote



import android.content.Intent

import android.os.Bundle

import android.widget.Toast

import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent

import androidx.activity.enableEdgeToEdge

import androidx.compose.runtime.CompositionLocalProvider

import androidx.lifecycle.compose.LocalLifecycleOwner

import androidx.lifecycle.lifecycleScope

import com.gluconote.data.repository.RecordRepository

import com.gluconote.data.repository.SettingsRepository

import com.gluconote.debug.DebugSeedData

import com.gluconote.ui.main.MainScreen

import com.gluconote.ui.theme.GlucoNoteTheme

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext



class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        setContent {

            // lifecycle-runtime-compose 2.8+ moved LocalLifecycleOwner out of compose.ui.

            // Explicitly provide it so collectAsStateWithLifecycle does not crash at startup.

            CompositionLocalProvider(LocalLifecycleOwner provides this@MainActivity) {

                GlucoNoteTheme {

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

            val label = if (seedLarge) "\u5927\u6570\u636e" else "\u8c03\u8bd5\u6570\u636e"
            Toast.makeText(this@MainActivity, "\u5df2\u52a0\u8f7d$label\uff1a$count \u6761", Toast.LENGTH_SHORT).show()

        }

    }



    companion object {

        const val EXTRA_SEED_DEBUG = "seed_debug"

        const val EXTRA_SEED_LARGE = "seed_large"

    }

}
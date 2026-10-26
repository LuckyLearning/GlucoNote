package com.gluconote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gluconote.ui.main.MainScreen
import com.gluconote.ui.theme.GlucoNoteTheme

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
    }
}

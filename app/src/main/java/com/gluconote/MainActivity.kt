package com.gluconote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gluconote.ui.main.MainScreen
import com.gluconote.ui.theme.GlucoNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlucoNoteTheme {
                MainScreen()
            }
        }
    }
}

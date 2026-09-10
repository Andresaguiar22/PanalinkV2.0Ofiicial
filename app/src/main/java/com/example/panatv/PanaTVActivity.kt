package com.example.panatv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class PanaTVActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PanaTVScreen()
                }
            }
        }
    }

    // PanaTV deliberately does NOT enter PiP automatically on user leave. Leaving
    // PanaTV always stops+releases playback (see PanaTVScreen lifecycle), so there
    // is no live player left to hand off to PiP. PiP is intentionally disabled for
    // PanaTV to keep player ownership simple and avoid ghost playback; Reels PiP
    // is unaffected. If manual PiP is desired later, it must be an explicit UI
    // action, never a side-effect of abandoning the screen.
}

package com.patslaurel.resibo.share

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patslaurel.resibo.ui.theme.ResiboTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Receives `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intents from any Android app.
 *
 * T026: only the intent-filter wiring is live. Parsing into `SharedPost` and
 * surfacing results lands in T027; edge cases (video/audio/oversized) in T029.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ResiboTheme {
                ShareReceiverPlaceholder()
            }
        }
    }
}

@Composable
private fun ShareReceiverPlaceholder() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
        ) {
            Text(
                text = "Received",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Parsing logic arrives in T027.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

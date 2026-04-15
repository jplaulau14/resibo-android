package com.patslaurel.resibo.share

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patslaurel.resibo.ui.theme.ResiboTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Receives `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intents from any Android app.
 *
 * T027: parses the incoming intent into a [SharedPost] and renders a summary screen.
 * Edge cases (video/audio/oversized) land in T029. Downstream normalization and the
 * full agent pipeline wire in from T031 onward.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val post = intent?.toSharedPost() ?: SharedPost()

        setContent {
            ResiboTheme {
                ShareReceiverScreen(
                    post = post,
                    onBack = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareReceiverScreen(
    post: SharedPost,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Shared to Resibo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Kind: ${post.kind.name.lowercase().replace('_', ' ')}",
                style = MaterialTheme.typography.titleMedium,
            )
            post.mimeType?.let {
                Text(
                    text = "MIME type: $it",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            post.referrer?.let {
                Text(
                    text = "Referrer: $it",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!post.text.isNullOrBlank()) {
                Text(
                    text = "Text",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (post.imageUris.isNotEmpty()) {
                Text(
                    text = "Images (${post.imageUris.size})",
                    style = MaterialTheme.typography.titleSmall,
                )
                post.imageUris.forEach { uri ->
                    Text(
                        text = uri.toString(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (post.isEmpty) {
                Text(
                    text = "Nothing extractable in this share. Try sharing text or an image.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

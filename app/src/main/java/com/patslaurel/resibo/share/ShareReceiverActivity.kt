package com.patslaurel.resibo.share

import android.content.Intent
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.patslaurel.resibo.llm.LlmTriageEngine
import com.patslaurel.resibo.ui.theme.ResiboTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Receives ACTION_SEND / ACTION_SEND_MULTIPLE intents from any Android app.
 *
 * Tonight (T044 MVP): a "Generate with Gemma" button runs [LlmTriageEngine] against
 * the shared text and renders the completion + wall-clock latency. The downstream
 * agent pipeline (claim decomposition, RAG, Note assembly) wires in from T046+.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {
    @Inject lateinit var llmEngine: LlmTriageEngine

    private var post by mutableStateOf(SharedPost())
    private var generation by mutableStateOf<GenerationState>(GenerationState.Idle)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        post = intent?.toSharedPost(contentResolver) ?: SharedPost()

        setContent {
            ResiboTheme {
                ShareReceiverScreen(
                    post = post,
                    generation = generation,
                    onBack = { finish() },
                    onGenerate = ::runGeneration,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        post = intent.toSharedPost(contentResolver)
        generation = GenerationState.Idle
    }

    private fun runGeneration(prompt: String) {
        if (generation is GenerationState.Generating) return
        generation = GenerationState.Generating
        lifecycleScope.launch {
            val started = System.currentTimeMillis()
            val outcome =
                runCatching {
                    withContext(Dispatchers.Default) {
                        llmEngine.generate(prompt)
                    }
                }
            val elapsed = System.currentTimeMillis() - started
            generation =
                outcome.fold(
                    onSuccess = { text -> GenerationState.Result(text, elapsed) },
                    onFailure = { t -> GenerationState.Error(t.message ?: t.javaClass.simpleName) },
                )
        }
    }
}

sealed interface GenerationState {
    data object Idle : GenerationState

    data object Generating : GenerationState

    data class Result(
        val text: String,
        val elapsedMs: Long,
    ) : GenerationState

    data class Error(
        val message: String,
    ) : GenerationState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareReceiverScreen(
    post: SharedPost,
    generation: GenerationState,
    onBack: () -> Unit,
    onGenerate: (prompt: String) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Kind: ${post.kind.displayLabel()}",
                style = MaterialTheme.typography.titleMedium,
            )

            post.warnings.forEach { WarningCard(it) }

            when (post.kind) {
                SharedPost.Kind.EMPTY -> EmptyMessage()
                SharedPost.Kind.URL_ONLY -> UrlOnlyMessage(post.text.orEmpty())
                else -> PostDetails(post)
            }

            if (!post.text.isNullOrBlank() && post.kind != SharedPost.Kind.URL_ONLY) {
                GenerationPanel(
                    prompt = post.text,
                    state = generation,
                    onGenerate = onGenerate,
                )
            }
        }
    }
}

@Composable
private fun GenerationPanel(
    prompt: String,
    state: GenerationState,
    onGenerate: (String) -> Unit,
) {
    Button(
        onClick = { onGenerate(prompt) },
        enabled = state !is GenerationState.Generating,
    ) {
        Text(
            when (state) {
                is GenerationState.Generating -> "Generating…"
                else -> "Generate with Gemma"
            },
        )
    }

    when (state) {
        GenerationState.Idle -> Unit

        GenerationState.Generating -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text(
                    text = "Running Gemma 3 1B on CPU — first call loads the model (~15–30s), subsequent calls are faster.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        is GenerationState.Result ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Response (${state.elapsedMs} ms)", style = MaterialTheme.typography.titleSmall)
                    Text(state.text, style = MaterialTheme.typography.bodyMedium)
                }
            }

        is GenerationState.Error ->
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Inference failed", style = MaterialTheme.typography.titleSmall)
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                }
            }
    }
}

@Composable
private fun PostDetails(post: SharedPost) {
    post.mimeType?.let {
        Text("MIME type: $it", style = MaterialTheme.typography.bodySmall)
    }
    post.referrer?.let {
        Text("Referrer: $it", style = MaterialTheme.typography.bodySmall)
    }
    if (!post.text.isNullOrBlank()) {
        Text("Text", style = MaterialTheme.typography.titleSmall)
        Text(post.text, style = MaterialTheme.typography.bodyMedium)
    }
    UriList("Images (${post.imageUris.size})", post.imageUris)
    UriList("Videos (${post.videoUris.size})", post.videoUris)
    UriList("Audio (${post.audioUris.size})", post.audioUris)
}

@Composable
private fun UriList(
    title: String,
    uris: List<android.net.Uri>,
) {
    if (uris.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall)
    uris.forEach { uri ->
        Text(uri.toString(), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyMessage() {
    Text(
        text = "Nothing extractable in this share. Try sharing text, an image, a short video, or an audio clip.",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun UrlOnlyMessage(url: String) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "This is just a link",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    "Resibo works fully offline, so we can't open the page behind this URL. " +
                        "Take a screenshot of the post itself and share the screenshot to Resibo instead.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun WarningCard(warning: SharedPost.Warning) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
    ) {
        Text(
            text = warning.displayLabel(),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun SharedPost.Kind.displayLabel(): String =
    when (this) {
        SharedPost.Kind.EMPTY -> "empty"
        SharedPost.Kind.URL_ONLY -> "url only"
        SharedPost.Kind.TEXT_ONLY -> "text only"
        SharedPost.Kind.SINGLE_IMAGE -> "single image"
        SharedPost.Kind.MULTIPLE_IMAGES -> "multiple images"
        SharedPost.Kind.TEXT_AND_IMAGES -> "text and images"
        SharedPost.Kind.VIDEO -> "video"
        SharedPost.Kind.AUDIO -> "audio"
    }

private fun SharedPost.Warning.displayLabel(): String =
    when (this) {
        SharedPost.Warning.OVERSIZED_IMAGE ->
            "One of the shared images is over 10 MB. Try a screenshot instead of a raw/HDR photo."

        SharedPost.Warning.UNSUPPORTED_MIME ->
            "The shared MIME type isn't supported yet."
    }

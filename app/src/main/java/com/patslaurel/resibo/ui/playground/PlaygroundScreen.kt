package com.patslaurel.resibo.ui.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patslaurel.resibo.llm.GenerationState
import com.patslaurel.resibo.ui.components.MarkdownText
import com.patslaurel.resibo.ui.theme.ResiboTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundScreen(
    onBack: () -> Unit = {},
    viewModel: PlaygroundViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Playground") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Free-text Gemma prompt. Uses the same triage system prompt as the share flow.",
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                label = { Text("Your text") },
                placeholder = { Text("Paste a claim, a post, a rumor…") },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = viewModel::generate,
                    enabled =
                        state.input.isNotBlank() &&
                            state.generation !is GenerationState.Generating,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when (state.generation) {
                            is GenerationState.Generating -> "Generating…"
                            else -> "Generate with Gemma"
                        },
                    )
                }
                if (state.generation !is GenerationState.Idle &&
                    state.generation !is GenerationState.Generating
                ) {
                    TextButton(onClick = viewModel::reset) {
                        Text("Clear")
                    }
                }
            }

            GenerationResult(state.generation)
        }
    }
}

@Composable
private fun GenerationResult(state: GenerationState) {
    when (state) {
        GenerationState.Idle -> Unit

        is GenerationState.CacheHit -> Unit

        // not applicable in Playground

        GenerationState.Generating -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text(
                    text = "First call after launch loads the model (~3–10 s on Fold3), subsequent calls run inference only.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        is GenerationState.Result ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Response (${state.elapsedMs} ms)", style = MaterialTheme.typography.titleSmall)
                    MarkdownText(state.text)
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun PlaygroundScreenPreview() {
    ResiboTheme {
        PlaygroundScreen(onBack = {})
    }
}

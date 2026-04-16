package com.patslaurel.resibo.ui.check

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patslaurel.resibo.ui.components.NoteCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CheckScreen(
    viewModel: CheckViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { viewModel.attachImage(it) }
        }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.checkPendingShare()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val screenState = deriveScreenState(state)

        AnimatedContent(
            targetState = screenState,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(tween(350)) + expandVertically(),
                    initialContentExit = fadeOut(tween(250)) + shrinkVertically(),
                )
            },
            label = "check_screen_transition",
        ) { target ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (target) {
                    ScreenState.IDLE -> {
                        IdleContent(
                            state = state,
                            onInputChange = viewModel::onInputChange,
                            onCheck = viewModel::check,
                            onAttach = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            onRemoveAttachment = viewModel::removeAttachment,
                        )
                    }

                    ScreenState.LOADING -> {
                        LoadingContent(state = state)
                    }

                    ScreenState.RESULT -> {
                        ResultContent(
                            state = state,
                            onReset = viewModel::reset,
                        )
                    }

                    ScreenState.ERROR -> {
                        ErrorContent(
                            state = state,
                            onReset = viewModel::reset,
                        )
                    }
                }
            }
        }
    }
}

private enum class ScreenState { IDLE, LOADING, RESULT, ERROR }

private fun deriveScreenState(state: CheckUiState): ScreenState =
    when (state.currentStep) {
        CheckStep.IDLE -> if (state.result != null) ScreenState.RESULT else ScreenState.IDLE
        CheckStep.THINKING, CheckStep.TOOL_CALLING -> ScreenState.LOADING
        CheckStep.GENERATING_NOTE -> if (state.result?.analysis.isNullOrEmpty()) ScreenState.LOADING else ScreenState.RESULT
        CheckStep.DONE -> ScreenState.RESULT
        CheckStep.ERROR -> ScreenState.ERROR
    }

@Composable
private fun BrandHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Resibo",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleContent(
    state: CheckUiState,
    onInputChange: (String) -> Unit,
    onCheck: () -> Unit,
    onAttach: () -> Unit,
    onRemoveAttachment: () -> Unit,
) {
    BrandHeader()

    Spacer(Modifier.height(24.dp))

    SearchBarInput(
        value = state.inputText,
        onValueChange = onInputChange,
        onSend = onCheck,
        onAttach = onAttach,
        attachedImageUri = state.attachedImageUri,
        onRemoveAttachment = onRemoveAttachment,
        sendEnabled = state.inputText.isNotBlank() || state.attachedImageUri != null,
    )

    Spacer(Modifier.height(8.dp))

    val suggestions =
        listOf(
            "Sabi sa FB, bawal na daw mag-share ng memes sa internet",
            "May nakita akong post na nagbebenta ng COVID cure sa Shopee",
            "Totoo ba na libre na ang MRT simula next month?",
            "Narinig ko sa TikTok na may incoming na magnitude 10 earthquake sa Manila",
            "May kumakalat na video ni VP Sara na AI-generated daw",
        )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { chip ->
            SuggestionChip(
                onClick = { onInputChange(chip) },
                label = {
                    Text(
                        chip,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
        }
    }

    Spacer(Modifier.height(48.dp))

    Text(
        text = "Powered by Gemma 4 \u00b7 On-device AI",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
    )
}

@Composable
private fun SearchBarInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    attachedImageUri: Uri?,
    onRemoveAttachment: () -> Unit,
    sendEnabled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.7f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            attachedImageUri?.let { uri ->
                Box(modifier = Modifier.padding(start = 4.dp)) {
                    InlineImageThumbnail(uri = uri)
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove attachment",
                        modifier =
                            Modifier
                                .size(16.dp)
                                .align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = enabled) { onRemoveAttachment() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
            } ?: run {
                IconButton(onClick = onAttach, enabled = enabled) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Attach image",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Paste, type, or share something\u2026",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            IconButton(
                onClick = onSend,
                enabled = enabled && sendEnabled,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Check",
                    tint =
                        if (sendEnabled && enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(state: CheckUiState) {
    BrandHeader()

    Spacer(Modifier.height(8.dp))

    SearchBarInput(
        value = state.inputText.ifEmpty { "Checking\u2026" },
        onValueChange = {},
        onSend = {},
        onAttach = {},
        attachedImageUri = state.attachedImageUri,
        onRemoveAttachment = {},
        sendEnabled = false,
        enabled = false,
    )

    Spacer(Modifier.height(16.dp))

    val steps =
        listOf(
            CheckStep.THINKING to "Analyzing claim",
            CheckStep.TOOL_CALLING to
                if (state.activeToolName.isNotBlank()) {
                    "Calling ${state.activeToolName}${if (state.activeToolInput.isNotBlank()) {
                        ": ${state.activeToolInput.take(
                            40,
                        )}"
                    } else {
                        ""
                    }}"
                } else {
                    "Using tools"
                },
            CheckStep.GENERATING_NOTE to "Writing fact-check Note",
        )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseColor by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.onSurface,
        targetValue = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        animationSpec =
            infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse_color",
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        steps.forEach { (step, label) ->
            val isCompleted = step.ordinal < state.currentStep.ordinal
            val isActive = step == state.currentStep

            AnimatedVisibility(
                visible = isCompleted || isActive || step.ordinal <= state.currentStep.ordinal,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        isCompleted -> {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Done",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        isActive -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = pulseColor,
                            )
                        }

                        else -> {
                            Icon(
                                Icons.Outlined.Circle,
                                contentDescription = "Pending",
                                tint = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultContent(
    state: CheckUiState,
    onReset: () -> Unit,
) {
    BrandHeader()

    Spacer(Modifier.height(8.dp))

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onReset() },
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = state.result?.claim ?: state.inputText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }

    AnimatedVisibility(
        visible = state.result != null,
        enter = fadeIn(tween(400)) + expandVertically(tween(400)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.result?.let { result ->
                NoteCard(checkResult = result)
            }

            if (state.currentStep == CheckStep.DONE) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Check something else")
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    state: CheckUiState,
    onReset: () -> Unit,
) {
    BrandHeader()

    Spacer(Modifier.height(16.dp))

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
    ) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = state.errorMessage ?: "An unexpected error occurred.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = onReset) {
                    Text("Try again")
                }
            }
        }
    }
}

@Composable
private fun InlineImageThumbnail(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
            }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Attached image",
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

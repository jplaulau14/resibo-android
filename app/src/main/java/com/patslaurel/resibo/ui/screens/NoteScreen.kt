package com.patslaurel.resibo.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.patslaurel.resibo.data.NoteRepository
import com.patslaurel.resibo.data.entity.NoteEntity
import com.patslaurel.resibo.ui.components.MarkdownText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class NoteHistoryViewModel
    @Inject
    constructor(
        noteRepository: NoteRepository,
    ) : ViewModel() {
        val notes: StateFlow<List<NoteEntity>> =
            noteRepository
                .observeAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    modifier: Modifier = Modifier,
    viewModel: NoteHistoryViewModel = hiltViewModel(),
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("History") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        if (notes.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = "No Notes yet",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = "Share a post or use the Playground to generate your first Note.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(note)
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: NoteEntity) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .clickable { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = note.claim,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (note.domain != "unknown") {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(note.domain, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier,
                    )
                }
                if (note.checkWorthiness != "unknown") {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(note.checkWorthiness, style = MaterialTheme.typography.labelSmall) },
                        colors =
                            SuggestionChipDefaults.suggestionChipColors(
                                containerColor =
                                    when (note.checkWorthiness) {
                                        "high" -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                            ),
                    )
                }
            }

            Text(
                text = "${formatDuration(note.generationMs)}  ·  ${formatTimestamp(note.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )

            if (expanded) {
                MarkdownText(
                    markdown = note.fullResponse,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

private fun formatTimestamp(millis: Long): String = dateFormat.format(Date(millis))

private fun formatDuration(ms: Long): String =
    when {
        ms < 1000 -> "${ms}ms"
        else -> "${"%.1f".format(ms / 1000.0)}s"
    }

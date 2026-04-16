package com.patslaurel.resibo.ui.playground

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patslaurel.resibo.data.NoteRepository
import com.patslaurel.resibo.data.entity.NoteEntity
import com.patslaurel.resibo.llm.GenerationState
import com.patslaurel.resibo.llm.LlmTriageEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PlaygroundViewModel
    @Inject
    constructor(
        private val engine: LlmTriageEngine,
        private val noteRepository: NoteRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PlaygroundUiState())
        val state: StateFlow<PlaygroundUiState> = _state.asStateFlow()

        fun onInputChange(value: String) {
            _state.update { it.copy(input = value) }
        }

        fun generate() {
            val prompt = _state.value.input.trim()
            if (prompt.isEmpty() || _state.value.generation is GenerationState.Generating) return

            _state.update { it.copy(generation = GenerationState.Generating) }
            viewModelScope.launch {
                val started = System.currentTimeMillis()
                val outcome =
                    runCatching {
                        withContext(Dispatchers.Default) { engine.generate(prompt) }
                    }
                val elapsed = System.currentTimeMillis() - started
                val newState =
                    outcome.fold(
                        onSuccess = { text -> GenerationState.Result(text, elapsed) },
                        onFailure = { t -> GenerationState.Error(t.message ?: t.javaClass.simpleName) },
                    )
                _state.update { it.copy(generation = newState) }

                if (newState is GenerationState.Result) {
                    saveNote(prompt, newState)
                }
            }
        }

        fun reset() {
            _state.update { it.copy(generation = GenerationState.Idle) }
        }

        private suspend fun saveNote(
            prompt: String,
            result: GenerationState.Result,
        ) {
            runCatching {
                noteRepository.saveNote(
                    NoteEntity(
                        claim = prompt.take(500),
                        language = "auto",
                        checkWorthiness = "unknown",
                        domain = "unknown",
                        offlineAssessment = "",
                        verificationNeeded = "",
                        fullResponse = result.text,
                        modelVariant = "gemma-4-e2b-it",
                        promptChars = prompt.length,
                        outputChars = result.text.length,
                        generationMs = result.elapsedMs,
                        mimeType = "text/plain",
                    ),
                )
            }
        }
    }

data class PlaygroundUiState(
    val input: String = "",
    val generation: GenerationState = GenerationState.Idle,
)

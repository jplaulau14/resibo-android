package com.patslaurel.resibo.ui.playground

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * Backing state for the free-text Gemma playground. One-shot generation — no
 * conversation history, no streaming. Swap to [com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession]
 * when we need multi-turn or token streaming.
 */
@HiltViewModel
class PlaygroundViewModel
    @Inject
    constructor(
        private val engine: LlmTriageEngine,
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
                _state.update {
                    it.copy(
                        generation =
                            outcome.fold(
                                onSuccess = { text -> GenerationState.Result(text, elapsed) },
                                onFailure = { t -> GenerationState.Error(t.message ?: t.javaClass.simpleName) },
                            ),
                    )
                }
            }
        }

        fun reset() {
            _state.update { it.copy(generation = GenerationState.Idle) }
        }
    }

data class PlaygroundUiState(
    val input: String = "",
    val generation: GenerationState = GenerationState.Idle,
)

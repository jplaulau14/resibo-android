package com.patslaurel.resibo.ui.check

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patslaurel.resibo.agent.AgentEvent
import com.patslaurel.resibo.agent.AgentRunner
import com.patslaurel.resibo.data.NoteRepository
import com.patslaurel.resibo.data.entity.NoteEntity
import com.patslaurel.resibo.data.entity.SourceEntity
import com.patslaurel.resibo.factcheck.FactCheckResult
import com.patslaurel.resibo.hash.ImageHasher
import com.patslaurel.resibo.hash.PerceptualHash
import com.patslaurel.resibo.llm.NoteParser
import com.patslaurel.resibo.share.PendingShare
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CheckViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val agentRunner: AgentRunner,
        private val noteRepository: NoteRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(CheckUiState())
        val state: StateFlow<CheckUiState> = _state.asStateFlow()

        companion object {
            private const val TAG = "CheckViewModel"
        }

        fun onInputChange(value: String) {
            _state.update { it.copy(inputText = value) }
        }

        fun attachImage(uri: Uri) {
            _state.update { it.copy(attachedImageUri = uri) }
        }

        fun removeAttachment() {
            _state.update { it.copy(attachedImageUri = null) }
        }

        fun check() {
            val text = _state.value.inputText.trim()
            val imageUri = _state.value.attachedImageUri
            if (text.isEmpty() && imageUri == null) return
            if (_state.value.currentStep != CheckStep.IDLE && _state.value.currentStep != CheckStep.DONE &&
                _state.value.currentStep != CheckStep.ERROR
            ) {
                return
            }

            val claim = text.ifEmpty { "Fact-check this image." }

            _state.update {
                it.copy(
                    currentStep = CheckStep.THINKING,
                    result = null,
                    errorMessage = null,
                    toolResults = emptyList(),
                )
            }

            viewModelScope.launch(Dispatchers.Default) {
                val totalStart = System.currentTimeMillis()
                val imageTempFile = imageUri?.let { copyToTemp(it) }
                val imageHash =
                    imageUri?.let {
                        withContext(Dispatchers.Default) {
                            ImageHasher.dHashFromUri(context.contentResolver, it)
                        }
                    }

                try {
                    var evidence = emptyList<FactCheckResult>()

                    agentRunner.run(claim, imageTempFile?.absolutePath).collect { event ->
                        when (event) {
                            is AgentEvent.ToolRequested -> {
                                _state.update {
                                    it.copy(
                                        currentStep = CheckStep.TOOL_CALLING,
                                        activeToolName = event.toolName,
                                        activeToolInput = event.input,
                                    )
                                }
                            }

                            is AgentEvent.ToolCompleted -> {
                                _state.update {
                                    it.copy(activeToolName = "", activeToolInput = "")
                                }
                            }

                            is AgentEvent.TokenGenerated -> {
                                _state.update {
                                    it.copy(
                                        currentStep = CheckStep.GENERATING_NOTE,
                                        result =
                                            CheckResult(
                                                claim = claim,
                                                analysis = event.fullText,
                                                sources = evidence,
                                                responseTimeMs = System.currentTimeMillis() - totalStart,
                                                imageUri = imageUri,
                                                toolsUsed = it.toolResults,
                                            ),
                                    )
                                }
                            }

                            is AgentEvent.Done -> {
                                evidence = event.perplexityResult.sources
                                val totalTime = System.currentTimeMillis() - totalStart
                                _state.update {
                                    it.copy(
                                        currentStep = CheckStep.DONE,
                                        toolResults = event.toolResults,
                                        result =
                                            CheckResult(
                                                claim = claim,
                                                analysis = event.finalNote,
                                                sources = evidence,
                                                responseTimeMs = totalTime,
                                                imageUri = imageUri,
                                                toolsUsed = event.toolResults,
                                            ),
                                    )
                                }
                                saveNote(claim, event.finalNote, imageHash, evidence)
                            }

                            is AgentEvent.Error -> {
                                _state.update {
                                    it.copy(
                                        currentStep = CheckStep.ERROR,
                                        errorMessage = event.message,
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Agent failed: ${e.message}", e)
                    _state.update {
                        it.copy(
                            currentStep = CheckStep.ERROR,
                            errorMessage = e.message ?: e.javaClass.simpleName,
                        )
                    }
                } finally {
                    imageTempFile?.delete()
                }
            }
        }

        fun reset() {
            _state.update { CheckUiState() }
        }

        fun checkPendingShare() {
            consumePendingShare()
        }

        private fun consumePendingShare() {
            if (!PendingShare.hasPending()) return
            val (text, imageUri) = PendingShare.consume()
            _state.update {
                it.copy(
                    inputText = text ?: "",
                    attachedImageUri = imageUri,
                )
            }
            check()
        }

        private suspend fun copyToTemp(uri: Uri): File? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val tempFile = File.createTempFile("resibo_check_", ".jpg", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    tempFile
                }.getOrNull()
            }

        private suspend fun saveNote(
            prompt: String,
            responseText: String,
            imageHash: Long?,
            evidence: List<FactCheckResult>,
        ) {
            runCatching {
                val parsed = NoteParser.parse(responseText)
                noteRepository.saveNote(
                    note =
                        NoteEntity(
                            claim = parsed.claim.ifEmpty { prompt.take(500) },
                            language = parsed.language,
                            checkWorthiness = parsed.checkWorthiness,
                            domain = parsed.domain,
                            offlineAssessment = parsed.offlineAssessment,
                            verificationNeeded = parsed.verificationNeeded,
                            fullResponse = responseText,
                            modelVariant = "gemma-4-e4b-it",
                            promptChars = prompt.length,
                            outputChars = responseText.length,
                            generationMs = 0,
                            mimeType = if (imageHash != null) "image/jpeg" else "text/plain",
                            perceptualHash = imageHash?.let { PerceptualHash.toHex(it) },
                        ),
                    sources =
                        evidence.map { src ->
                            SourceEntity(
                                noteId = 0,
                                title = src.reviewTitle.ifBlank { src.publisherName },
                                url = src.reviewUrl,
                                snippet = src.claimText,
                                domain = src.publisherName,
                            )
                        },
                )
            }
        }
    }

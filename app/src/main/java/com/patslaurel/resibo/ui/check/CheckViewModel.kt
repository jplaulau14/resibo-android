package com.patslaurel.resibo.ui.check

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patslaurel.resibo.data.NoteRepository
import com.patslaurel.resibo.data.entity.NoteEntity
import com.patslaurel.resibo.data.entity.SourceEntity
import com.patslaurel.resibo.factcheck.FactCheckResult
import com.patslaurel.resibo.factcheck.PerplexityClient
import com.patslaurel.resibo.factcheck.PerplexityResult
import com.patslaurel.resibo.hash.ImageHasher
import com.patslaurel.resibo.hash.PerceptualHash
import com.patslaurel.resibo.llm.LlmTriageEngine
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
        private val engine: LlmTriageEngine,
        private val noteRepository: NoteRepository,
        private val perplexity: PerplexityClient,
    ) : ViewModel() {
        private val _state = MutableStateFlow(CheckUiState())
        val state: StateFlow<CheckUiState> = _state.asStateFlow()

        companion object {
            private const val TAG = "CheckViewModel"
        }

        init {
            consumePendingShare()
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

            val prompt = text.ifEmpty { "Fact-check this image." }

            _state.update {
                it.copy(
                    inputText = "",
                    attachedImageUri = null,
                    currentStep = CheckStep.EXTRACTING_QUERY,
                    searchQuery = "",
                    sourceCount = 0,
                    result = null,
                    errorMessage = null,
                )
            }

            viewModelScope.launch {
                try {
                    val totalStart = System.currentTimeMillis()
                    val imageTempFile = imageUri?.let { copyToTemp(it) }
                    val imageHash =
                        imageUri?.let {
                            withContext(Dispatchers.Default) {
                                ImageHasher.dHashFromUri(context.contentResolver, it)
                            }
                        }

                    val searchQuery =
                        try {
                            withContext(Dispatchers.Default) {
                                engine.extractSearchKeywords(prompt)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Search query extraction failed: ${e.message}", e)
                            ""
                        }
                    Log.i(TAG, "Gemma search query: '$searchQuery'")

                    _state.update {
                        it.copy(
                            currentStep = CheckStep.SEARCHING_WEB,
                            searchQuery = searchQuery,
                        )
                    }

                    val pplxResult =
                        if (searchQuery.isNotBlank()) {
                            try {
                                withContext(Dispatchers.IO) { perplexity.search(searchQuery) }
                            } catch (e: Exception) {
                                Log.e(TAG, "Perplexity search failed: ${e.message}", e)
                                PerplexityResult.EMPTY
                            }
                        } else {
                            PerplexityResult.EMPTY
                        }
                    val evidence = pplxResult.sources
                    Log.i(TAG, "Perplexity: ${pplxResult.text.length}-char evidence")
                    val evidenceContext =
                        if (pplxResult.text.isNotBlank()) {
                            "## Web research results\n\n${pplxResult.text}\n\n---\n\n"
                        } else {
                            ""
                        }

                    _state.update {
                        it.copy(
                            currentStep = CheckStep.GENERATING_NOTE,
                            sourceCount = evidence.size,
                            result =
                                CheckResult(
                                    claim = prompt,
                                    analysis = "",
                                    sources = evidence,
                                    responseTimeMs = 0,
                                    imageUri = imageUri,
                                ),
                        )
                    }

                    val tokenFlow =
                        if (imageTempFile != null) {
                            try {
                                engine.generateWithImageStreaming(prompt, imageTempFile.absolutePath, evidenceContext)
                            } catch (_: Exception) {
                                engine.generateStreaming(prompt, evidenceContext)
                            }
                        } else {
                            engine.generateStreaming(prompt, evidenceContext)
                        }

                    var responseText = ""
                    tokenFlow.collect { token ->
                        responseText += token
                        _state.update {
                            it.copy(
                                result =
                                    it.result?.copy(
                                        analysis = responseText,
                                        responseTimeMs = System.currentTimeMillis() - totalStart,
                                    ),
                            )
                        }
                    }

                    imageTempFile?.delete()

                    Log.i(TAG, "Generation result (${responseText.length} chars): ${responseText.take(200)}...")

                    val totalTime = System.currentTimeMillis() - totalStart

                    _state.update {
                        it.copy(
                            currentStep = CheckStep.DONE,
                            result =
                                CheckResult(
                                    claim = prompt,
                                    analysis = responseText,
                                    sources = evidence,
                                    responseTimeMs = totalTime,
                                    imageUri = imageUri,
                                ),
                        )
                    }

                    saveNote(prompt, responseText, imageHash, evidence)
                } catch (e: Exception) {
                    Log.e(TAG, "Check failed: ${e.message}", e)
                    _state.update {
                        it.copy(
                            currentStep = CheckStep.ERROR,
                            errorMessage = e.message ?: e.javaClass.simpleName,
                        )
                    }
                }
            }
        }

        fun reset() {
            _state.update {
                CheckUiState()
            }
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

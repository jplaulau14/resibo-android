package com.patslaurel.resibo.ui.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patslaurel.resibo.data.NoteRepository
import com.patslaurel.resibo.data.entity.NoteEntity
import com.patslaurel.resibo.factcheck.EvidenceInjector
import com.patslaurel.resibo.factcheck.FactCheckApiClient
import com.patslaurel.resibo.factcheck.FactCheckResult
import com.patslaurel.resibo.hash.ImageHasher
import com.patslaurel.resibo.hash.PerceptualHash
import com.patslaurel.resibo.llm.LlmTriageEngine
import com.patslaurel.resibo.llm.NoteParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val engine: LlmTriageEngine,
        private val noteRepository: NoteRepository,
        private val factCheckApi: FactCheckApiClient,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ChatUiState())
        val state: StateFlow<ChatUiState> = _state.asStateFlow()

        /** Most recent fact-check evidence — exposed for the sources UI. */
        var lastEvidence: List<FactCheckResult> = emptyList()
            private set

        init {
            addWelcomeMessage()
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

        fun send() {
            val text = _state.value.inputText.trim()
            val imageUri = _state.value.attachedImageUri
            if (text.isEmpty() && imageUri == null) return
            if (_state.value.isGenerating) return

            val userMessage =
                ChatMessage(
                    role = ChatMessage.Role.USER,
                    text = text.ifEmpty { "Fact-check this image." },
                    imageUri = imageUri,
                )

            val generatingMessage =
                ChatMessage(
                    role = ChatMessage.Role.RESIBO,
                    text = "",
                    isGenerating = true,
                )

            _state.update {
                it.copy(
                    messages = it.messages + userMessage + generatingMessage,
                    inputText = "",
                    attachedImageUri = null,
                    isGenerating = true,
                )
            }

            viewModelScope.launch {
                val prompt = userMessage.text
                val imageTempFile = imageUri?.let { copyToTemp(it) }
                val imageHash =
                    imageUri?.let {
                        withContext(Dispatchers.Default) {
                            ImageHasher.dHashFromUri(context.contentResolver, it)
                        }
                    }

                // Query fact-check API in parallel with model preparation
                val evidenceDeferred =
                    async(Dispatchers.IO) {
                        runCatching { factCheckApi.search(prompt) }.getOrDefault(emptyList())
                    }

                val evidence = evidenceDeferred.await()
                val evidenceContext = EvidenceInjector.buildContext(evidence)

                val result =
                    runCatching {
                        withContext(Dispatchers.Default) {
                            if (imageTempFile != null) {
                                try {
                                    engine.generateWithImage(prompt, imageTempFile.absolutePath, evidenceContext)
                                } catch (_: Exception) {
                                    engine.generate(prompt, evidenceContext)
                                }
                            } else {
                                engine.generate(prompt, evidenceContext)
                            }
                        }
                    }

                imageTempFile?.delete()
                lastEvidence = evidence

                val responseText =
                    result.getOrElse { t ->
                        "Something went wrong: ${t.message ?: t.javaClass.simpleName}"
                    }

                _state.update { current ->
                    val updated =
                        current.messages.map { msg ->
                            if (msg.isGenerating) {
                                msg.copy(text = responseText, isGenerating = false)
                            } else {
                                msg
                            }
                        }
                    current.copy(messages = updated, isGenerating = false)
                }

                if (result.isSuccess) {
                    saveNote(prompt, responseText, imageHash)
                }
            }
        }

        fun clearChat() {
            _state.update { ChatUiState() }
            addWelcomeMessage()
        }

        private fun addWelcomeMessage() {
            val welcome =
                ChatMessage(
                    role = ChatMessage.Role.RESIBO,
                    text =
                        "I'm **Resibo**. Share a post — screenshot, text, or audio — and " +
                            "I'll give you a **Note**: sources, reasoning, and what I couldn't verify.\n\n" +
                            "Everything runs on your phone. Nothing leaves this device.",
                )
            _state.update { it.copy(messages = listOf(welcome)) }
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
        }

        private suspend fun copyToTemp(uri: Uri): File? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val tempFile = File.createTempFile("resibo_chat_", ".jpg", context.cacheDir)
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
        ) {
            runCatching {
                val parsed = NoteParser.parse(responseText)
                noteRepository.saveNote(
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
                )
            }
        }
    }

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val attachedImageUri: Uri? = null,
    val isGenerating: Boolean = false,
)

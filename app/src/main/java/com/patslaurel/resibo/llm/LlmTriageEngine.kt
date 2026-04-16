package com.patslaurel.resibo.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device Gemma triage engine. Wraps the Google AI Edge **LiteRT-LM** `Engine` API
 * for `.litertlm` model bundles (Gemma 4 E2B/E4B).
 *
 * Model loading is lazy — the first [generate] call pays the `Engine.initialize()` cost;
 * subsequent calls reuse the engine and create lightweight conversations. Call [close]
 * from the host Activity's onDestroy if you want to free memory eagerly; otherwise
 * process death handles it.
 *
 * Prompts live in `assets/prompts/` and can be overridden at runtime via adb push — see
 * [PromptLoader].
 */
@Singleton
class LlmTriageEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val promptLoader: PromptLoader,
    ) {
        @Volatile private var engine: Engine? = null

        /** Absolute path the model loader searches. Overridable via [generate] caller. */
        val defaultModelPath: File
            get() = File(context.getExternalFilesDir(null), MODEL_FILENAME)

        /**
         * Run the model on [userInput], wrapped with the triage system prompt.
         * Creates a fresh [Conversation] per call (stateless — each share is independent).
         *
         * @throws IllegalStateException when [modelPath] doesn't exist
         * @throws RuntimeException when LiteRT-LM itself fails (OOM, unsupported model)
         */
        fun generate(
            userInput: String,
            evidenceContext: String = "",
            modelPath: File = defaultModelPath,
        ): String {
            val eng = ensureLoaded(modelPath)
            val prompt = buildTriagePrompt(userInput, evidenceContext)
            val started = System.currentTimeMillis()
            val message =
                eng.createConversation().use { conversation ->
                    conversation.sendMessage(prompt)
                }
            val result =
                message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
            val elapsed = System.currentTimeMillis() - started
            Log.i(
                TAG,
                "generate(promptChars=${prompt.length}, outputChars=${result.length}) took ${elapsed}ms",
            )
            return result
        }

        /**
         * Run the model on [userInput] + an image file. The model sees both the text prompt
         * (wrapped with the triage system prompt) and the image. Requires a multimodal model
         * (Gemma 4 E4B) — will likely produce garbage or crash on text-only models (E2B).
         */
        fun generateWithImage(
            userInput: String,
            imagePath: String,
            evidenceContext: String = "",
            modelPath: File = defaultModelPath,
        ): String {
            val eng = ensureLoaded(modelPath)
            val system = promptLoader.load(PromptLoader.TRIAGE_SYSTEM)
            val evidenceBlock = if (evidenceContext.isNotBlank()) "\n$evidenceContext\n" else "\n---\n\n"
            val textPrompt =
                "${system.trim()}$evidenceBlock\nUser's shared post (with image attached):\n\n${userInput.trim()}"

            val contents =
                Contents.of(
                    Content.ImageFile(imagePath),
                    Content.Text(textPrompt),
                )

            val started = System.currentTimeMillis()
            val message =
                eng.createConversation().use { conversation ->
                    conversation.sendMessage(contents)
                }
            val result =
                message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
            val elapsed = System.currentTimeMillis() - started
            Log.i(
                TAG,
                "generateWithImage(promptChars=${textPrompt.length}, imagePath=$imagePath, " +
                    "outputChars=${result.length}) took ${elapsed}ms",
            )
            return result
        }

        /**
         * Run the model against an already-formatted prompt — bypasses system-prompt wrapping.
         */
        fun generateRaw(
            prompt: String,
            modelPath: File = defaultModelPath,
        ): String {
            val eng = ensureLoaded(modelPath)
            val message =
                eng.createConversation().use { conversation ->
                    conversation.sendMessage(prompt)
                }
            return message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
        }

        /**
         * Ask the model to extract English search keywords from any-language input.
         * Returns a short string of keywords for the Fact Check API.
         * Fast — tiny output budget, reuses already-loaded engine.
         */
        fun extractSearchKeywords(
            userInput: String,
            modelPath: File = defaultModelPath,
        ): String {
            val eng = ensureLoaded(modelPath)
            val template = promptLoader.load(PromptLoader.KEYWORD_EXTRACTION)
            val prompt = template.replace("{INPUT}", userInput.trim().take(500))
            Log.i(TAG, "extractSearchKeywords: sending ${prompt.length}-char prompt")
            val started = System.currentTimeMillis()
            val message =
                eng.createConversation().use { conversation ->
                    conversation.sendMessage(prompt)
                }
            val rawResult =
                message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                    .trim()
            val result = rawResult.lines().firstOrNull()?.trim() ?: rawResult.take(100).trim()
            val elapsed = System.currentTimeMillis() - started
            Log.i(TAG, "extractSearchKeywords took ${elapsed}ms → '$result' (raw: '${rawResult.take(80)}')")
            return result
        }

        /** Free the underlying LiteRT-LM engine. Safe to call multiple times. */
        @Synchronized
        fun close() {
            engine?.close()
            engine = null
        }

        /**
         * Build the prompt, optionally prepending fact-check evidence from the API.
         * When [evidenceContext] is non-empty, the model sees real sources before the
         * user's claim — dramatically improving citation quality.
         */
        fun buildTriagePrompt(
            userInput: String,
            evidenceContext: String = "",
        ): String {
            val system = promptLoader.load(PromptLoader.TRIAGE_SYSTEM)
            return if (evidenceContext.isNotBlank()) {
                "${system.trim()}\n\n$evidenceContext\nUser's shared post:\n\n${userInput.trim()}"
            } else {
                "${system.trim()}\n\n---\n\nUser's shared post:\n\n${userInput.trim()}"
            }
        }

        @Synchronized
        private fun ensureLoaded(modelPath: File): Engine {
            engine?.let { return it }
            check(modelPath.exists()) {
                "Model not found at ${modelPath.absolutePath}. Push the .litertlm file via: " +
                    "adb push <local>.litertlm ${modelPath.absolutePath}"
            }
            val sizeMb = modelPath.length() / 1_048_576
            Log.i(TAG, "Loading Gemma 4 from ${modelPath.absolutePath} ($sizeMb MB)")
            val useGpu = sizeMb > 3000
            val textBackend =
                if (useGpu) {
                    Log.i(TAG, "Large model detected — using GPU backend to avoid XNNPACK weight-cache bug")
                    Backend.GPU()
                } else {
                    Backend.CPU()
                }
            val config =
                EngineConfig(
                    modelPath = modelPath.absolutePath,
                    backend = textBackend,
                    visionBackend = Backend.GPU(),
                    audioBackend = Backend.CPU(),
                )
            val eng = Engine(config)
            eng.initialize()
            Log.i(TAG, "Gemma 4 loaded.")
            engine = eng
            return eng
        }

        companion object {
            private const val TAG = "LlmTriageEngine"

            /** Side-load: `adb push <file>.litertlm /sdcard/Android/data/com.patslaurel.resibo/files/$MODEL_FILENAME` */
            const val MODEL_FILENAME = "gemma.litertlm"
        }
    }

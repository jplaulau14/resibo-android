package com.patslaurel.resibo.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Content
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
            modelPath: File = defaultModelPath,
        ): String {
            val eng = ensureLoaded(modelPath)
            val prompt = buildTriagePrompt(userInput)
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

        /** Free the underlying LiteRT-LM engine. Safe to call multiple times. */
        @Synchronized
        fun close() {
            engine?.close()
            engine = null
        }

        private fun buildTriagePrompt(userInput: String): String {
            val system = promptLoader.load(PromptLoader.TRIAGE_SYSTEM)
            return "${system.trim()}\n\n---\n\nUser's shared post:\n\n${userInput.trim()}"
        }

        @Synchronized
        private fun ensureLoaded(modelPath: File): Engine {
            engine?.let { return it }
            check(modelPath.exists()) {
                "Model not found at ${modelPath.absolutePath}. Push the .litertlm file via: " +
                    "adb push <local>.litertlm ${modelPath.absolutePath}"
            }
            Log.i(TAG, "Loading Gemma 4 from ${modelPath.absolutePath} (${modelPath.length() / 1_048_576} MB)")
            val config = EngineConfig(modelPath = modelPath.absolutePath)
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

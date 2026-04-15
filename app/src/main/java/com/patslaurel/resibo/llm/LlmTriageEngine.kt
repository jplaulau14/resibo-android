package com.patslaurel.resibo.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device Gemma triage engine. Wraps the MediaPipe `LlmInference` API for the 1B-class
 * model used by the agent's first-pass triage step (language detect, check-worthiness,
 * atomic claim extraction, domain routing).
 *
 * Model loading is lazy — the first [generate] call pays the load cost; subsequent calls
 * reuse the same [LlmInference] instance.
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
        @Volatile private var llm: LlmInference? = null

        /** Absolute path the model loader searches first. Overridable via [generate] caller. */
        val defaultModelPath: File
            get() = File(context.getExternalFilesDir(null), MODEL_FILENAME)

        /**
         * Run the model on [userInput], wrapped with the triage system prompt and Gemma's
         * `<start_of_turn>` chat format. Returns the completion.
         *
         * @param userInput raw text extracted from the shared post (the user's content — not
         *   the system prompt; we wrap that for you)
         * @param modelPath path to a `.task` bundle; defaults to [defaultModelPath]
         * @param maxTokens upper bound on the response length
         * @throws IllegalStateException when [modelPath] doesn't exist — catch and prompt the
         *   user to side-load the `.task` via adb
         */
        fun generate(
            userInput: String,
            modelPath: File = defaultModelPath,
            maxTokens: Int = DEFAULT_CONTEXT_TOKENS,
        ): String {
            val engine = ensureLoaded(modelPath, maxTokens)
            val prompt = buildTriagePrompt(userInput)
            val started = System.currentTimeMillis()
            val result = engine.generateResponse(prompt)
            val elapsed = System.currentTimeMillis() - started
            Log.i(
                TAG,
                "generate(maxTokens=$maxTokens, promptChars=${prompt.length}, outputChars=${result.length}) " +
                    "took ${elapsed}ms",
            )
            return result
        }

        /**
         * Run the model against an already-formatted prompt — bypasses system-prompt wrapping.
         * Useful for diagnostics or when upstream code has its own templating.
         */
        fun generateRaw(
            prompt: String,
            modelPath: File = defaultModelPath,
            maxTokens: Int = DEFAULT_CONTEXT_TOKENS,
        ): String {
            val engine = ensureLoaded(modelPath, maxTokens)
            return engine.generateResponse(prompt)
        }

        /** Free the underlying MediaPipe session. Safe to call multiple times. */
        @Synchronized
        fun close() {
            llm?.close()
            llm = null
        }

        /**
         * Build the prompt MediaPipe's `.task` runtime will feed to Gemma.
         *
         * Intentionally **no** explicit `<start_of_turn>` / `<end_of_turn>` tokens — Kaggle's
         * Gemma 3 IT `.task` bundle ships with chat-template metadata and MediaPipe applies
         * the tokens internally. Double-wrapping confuses the tokenizer and has been
         * observed to hang / crash inference on small models.
         */
        private fun buildTriagePrompt(userInput: String): String {
            val system = promptLoader.load(PromptLoader.TRIAGE_SYSTEM)
            return "${system.trim()}\n\n---\n\nUser's shared post:\n\n${userInput.trim()}"
        }

        @Synchronized
        private fun ensureLoaded(
            modelPath: File,
            maxTokens: Int,
        ): LlmInference {
            llm?.let { return it }
            check(modelPath.exists()) {
                "Model not found at ${modelPath.absolutePath}. Push the .task file via: " +
                    "adb push <local>.task ${modelPath.absolutePath}"
            }
            Log.i(TAG, "Loading Gemma from ${modelPath.absolutePath} (${modelPath.length() / 1_048_576} MB)")
            val options =
                LlmInference.LlmInferenceOptions
                    .builder()
                    .setModelPath(modelPath.absolutePath)
                    .setMaxTokens(maxTokens)
                    .build()
            val engine = LlmInference.createFromOptions(context, options)
            llm = engine
            Log.i(TAG, "Gemma loaded.")
            return engine
        }

        companion object {
            private const val TAG = "LlmTriageEngine"

            /** Side-load via `adb push <file>.task /sdcard/Android/data/com.patslaurel.resibo/files/$MODEL_FILENAME` */
            const val MODEL_FILENAME = "gemma.task"

            /**
             * MediaPipe `setMaxTokens` sets the **total** context window (prompt + output).
             * Gemma 3 1B supports up to 8192, but we don't need anywhere near that and
             * allocating a larger KV cache costs RAM. 2048 comfortably fits our system
             * prompt (~600 tokens) + user input + a 300-token Note response, without
             * blowing memory on SD888.
             */
            private const val DEFAULT_CONTEXT_TOKENS = 2048
        }
    }

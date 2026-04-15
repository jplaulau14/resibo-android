package com.patslaurel.resibo.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device Gemma triage engine. Wraps the MediaPipe `LlmInference` API for the
 * 1B-class model used by the agent's first-pass triage step (language detect,
 * check-worthiness, atomic claim extraction).
 *
 * Model loading is lazy — the first [generate] call pays the load cost; subsequent
 * calls reuse the same [LlmInference] instance. Call [close] from the host Activity's
 * onDestroy if you want to free memory eagerly; otherwise process death handles it.
 *
 * Tonight's scope (T044 MVP): single-shot `generate(prompt)` returning the full
 * completion. Streaming via [LlmInferenceSession] and structured tool-call decoding
 * land in T045 and T050 respectively.
 */
@Singleton
class LlmTriageEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @Volatile private var llm: LlmInference? = null

        /** Absolute path the model loader searches first. Overridable via [generate] caller. */
        val defaultModelPath: File
            get() = File(context.getExternalFilesDir(null), MODEL_FILENAME)

        /**
         * Run the model on [prompt] and return the completion.
         *
         * @param modelPath path to a `.task` bundle. Defaults to [defaultModelPath].
         * @param maxTokens upper bound on the response length (defaults to 256 — enough
         *   for a Note draft, short enough to fit in ~90s on SD888 CPU).
         * @throws IllegalStateException when [modelPath] does not exist — caller should
         *   catch and prompt the user to side-load the `.task` via adb.
         * @throws RuntimeException when MediaPipe itself fails (OOM, unsupported model).
         */
        fun generate(
            prompt: String,
            modelPath: File = defaultModelPath,
            maxTokens: Int = 256,
        ): String {
            val engine = ensureLoaded(modelPath, maxTokens)
            val started = System.currentTimeMillis()
            val result = engine.generateResponse(prompt)
            val elapsed = System.currentTimeMillis() - started
            Log.i(TAG, "generate($maxTokens max tokens) took ${elapsed}ms")
            return result
        }

        /** Free the underlying MediaPipe session. Safe to call multiple times. */
        @Synchronized
        fun close() {
            llm?.close()
            llm = null
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
        }
    }

package com.patslaurel.resibo.llm

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads system prompts for on-device LLM engines.
 *
 * Lookup order per prompt filename:
 *   1. `$externalFilesDir/prompts/<filename>` — **runtime override** (editable via adb push
 *      without rebuilding the APK). Perfect for hackathon-pace iteration.
 *   2. `assets/prompts/<filename>` — **bundled default**, shipped with the APK.
 *
 * Overrides are re-read on every call so you can `adb push` a new prompt and see the effect
 * on the next generation without restarting the app. Production builds should prefer the
 * bundled assets (or a Hilt-provided read-only variant) for integrity.
 */
@Singleton
class PromptLoader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Load the named prompt from the external override dir if present, else the packaged asset.
         *
         * @throws IllegalStateException if the asset is missing (indicates a build misconfiguration).
         */
        fun load(name: String): String {
            val override = File(context.getExternalFilesDir(null), "$PROMPTS_DIR/$name")
            if (override.exists()) {
                Log.i(TAG, "Loading prompt override: ${override.absolutePath}")
                return override.readText()
            }

            return runCatching {
                context.assets
                    .open("$PROMPTS_DIR/$name")
                    .bufferedReader()
                    .use { it.readText() }
            }.getOrElse {
                error("Bundled prompt asset missing: $PROMPTS_DIR/$name. Did you commit it under app/src/main/assets/?")
            }
        }

        companion object {
            private const val TAG = "PromptLoader"
            private const val PROMPTS_DIR = "prompts"

            const val TRIAGE_SYSTEM = "triage_system.md"
            const val KEYWORD_EXTRACTION = "keyword_extraction.md"
            const val AGENT_SYSTEM = "agent_system.md"
        }
    }

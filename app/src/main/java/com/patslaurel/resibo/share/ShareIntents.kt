package com.patslaurel.resibo.share

import android.content.Context
import android.content.Intent

/**
 * Helpers for launching [ShareReceiverActivity] from within the app — e.g. test buttons
 * on HomeScreen, canned examples in Settings, or future "re-run this post" UX.
 *
 * Production shares come in as real OS `ACTION_SEND` intents from other apps, untouched
 * by this file.
 */
object ShareIntents {
    /** Set to `true` to trigger `LlmTriageEngine.generate` automatically on activity start. */
    const val EXTRA_AUTO_GENERATE = "com.patslaurel.resibo.share.AUTO_GENERATE"

    /** Canned Tagalog fact-check prompt for in-app smoke testing. */
    const val COVID_AUTISM_TEST =
        "Kumakalat sa Facebook na ang bakuna sa COVID ay nagiging sanhi ng autism. Totoo ba ito?"

    /** Launch the share receiver with a canned text payload; optionally auto-generate. */
    fun launchTextTest(
        context: Context,
        text: String = COVID_AUTISM_TEST,
        autoGenerate: Boolean = true,
    ) {
        val intent =
            Intent(context, ShareReceiverActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                if (autoGenerate) putExtra(EXTRA_AUTO_GENERATE, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }
}

package com.patslaurel.resibo.share

import android.net.Uri

/**
 * Lightweight singleton for passing shared content from [ShareReceiverActivity]
 * to CheckViewModel across the activity boundary. Consumed once on read.
 */
object PendingShare {
    var text: String? = null
    var imageUri: Uri? = null

    fun consume(): Pair<String?, Uri?> {
        val t = text
        val u = imageUri
        text = null
        imageUri = null
        return t to u
    }

    fun hasPending(): Boolean = text != null || imageUri != null
}

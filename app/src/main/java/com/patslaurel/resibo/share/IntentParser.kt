package com.patslaurel.resibo.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.IntentCompat

/**
 * Pure function extracting a [SharedPost] from an incoming share [Intent].
 *
 * Handles ACTION_SEND (text/plain plus any image MIME) and ACTION_SEND_MULTIPLE (images).
 * Returns a `SharedPost` with `isEmpty == true` if nothing extractable is found.
 */
fun Intent.toSharedPost(): SharedPost {
    val referrer =
        getStringExtra(Intent.EXTRA_REFERRER)
            ?: getParcelableExtraCompat(Intent.EXTRA_REFERRER, Uri::class.java)?.toString()

    return when (action) {
        Intent.ACTION_SEND -> parseSingle(referrer)
        Intent.ACTION_SEND_MULTIPLE -> parseMultiple(referrer)
        else -> SharedPost(mimeType = type, referrer = referrer)
    }
}

private fun Intent.parseSingle(referrer: String?): SharedPost {
    val text = getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
    val imageUri = getParcelableExtraCompat(Intent.EXTRA_STREAM, Uri::class.java)
    return SharedPost(
        text = text,
        imageUris = listOfNotNull(imageUri),
        mimeType = type,
        referrer = referrer,
    )
}

private fun Intent.parseMultiple(referrer: String?): SharedPost {
    val uris: List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
    val text = getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
    return SharedPost(
        text = text,
        imageUris = uris,
        mimeType = type,
        referrer = referrer,
    )
}

private fun <T> Intent.getParcelableExtraCompat(key: String, clazz: Class<T>): T? =
    IntentCompat.getParcelableExtra(this, key, clazz)

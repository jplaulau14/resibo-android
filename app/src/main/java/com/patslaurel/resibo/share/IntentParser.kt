package com.patslaurel.resibo.share

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat

/**
 * Pure function extracting a [SharedPost] from an incoming share [Intent].
 *
 * Handles ACTION_SEND (text/plain, image, video, audio) and ACTION_SEND_MULTIPLE
 * (image, video). A [ContentResolver], when supplied, is used for oversized-image
 * detection; omitting it is fine for unit tests.
 */
fun Intent.toSharedPost(resolver: ContentResolver? = null): SharedPost {
    val referrer =
        getStringExtra(Intent.EXTRA_REFERRER)
            ?: getParcelableExtraCompat(Intent.EXTRA_REFERRER, Uri::class.java)?.toString()

    return when (action) {
        Intent.ACTION_SEND -> parseSingle(referrer, resolver)
        Intent.ACTION_SEND_MULTIPLE -> parseMultiple(referrer, resolver)
        else -> SharedPost(mimeType = type, referrer = referrer)
    }
}

private fun Intent.parseSingle(
    referrer: String?,
    resolver: ContentResolver?,
): SharedPost {
    val text = getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
    val streamUri = getParcelableExtraCompat(Intent.EXTRA_STREAM, Uri::class.java)
    val mime = type

    val imageUris = if (mime?.startsWith("image/") == true) listOfNotNull(streamUri) else emptyList()
    val videoUris = if (mime?.startsWith("video/") == true) listOfNotNull(streamUri) else emptyList()
    val audioUris = if (mime?.startsWith("audio/") == true) listOfNotNull(streamUri) else emptyList()

    val warnings = collectWarnings(imageUris, resolver)

    return SharedPost(
        text = text,
        imageUris = imageUris,
        videoUris = videoUris,
        audioUris = audioUris,
        mimeType = mime,
        referrer = referrer,
        warnings = warnings,
    )
}

private fun Intent.parseMultiple(
    referrer: String?,
    resolver: ContentResolver?,
): SharedPost {
    val uris = parcelableArrayListExtra()
    val text = getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
    val mime = type

    val imageUris = if (mime?.startsWith("image/") == true) uris else emptyList()
    val videoUris = if (mime?.startsWith("video/") == true) uris else emptyList()

    val warnings = collectWarnings(imageUris, resolver)

    return SharedPost(
        text = text,
        imageUris = imageUris,
        videoUris = videoUris,
        mimeType = mime,
        referrer = referrer,
        warnings = warnings,
    )
}

private fun Intent.parcelableArrayListExtra(): List<Uri> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }

private fun collectWarnings(
    imageUris: List<Uri>,
    resolver: ContentResolver?,
): List<SharedPost.Warning> =
    buildList {
        if (resolver != null && imageUris.any { isOversized(resolver, it) }) {
            add(SharedPost.Warning.OVERSIZED_IMAGE)
        }
    }

/** Threshold chosen to catch HDR raw photos; normal screenshots sit well under 5 MB. */
private const val OVERSIZED_IMAGE_THRESHOLD_BYTES = 10L * 1024 * 1024

private fun isOversized(
    resolver: ContentResolver,
    uri: Uri,
): Boolean =
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use false
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            idx >= 0 && !cursor.isNull(idx) && cursor.getLong(idx) > OVERSIZED_IMAGE_THRESHOLD_BYTES
        } ?: false
    }.getOrDefault(false)

private fun <T> Intent.getParcelableExtraCompat(
    key: String,
    clazz: Class<T>,
): T? = IntentCompat.getParcelableExtra(this, key, clazz)

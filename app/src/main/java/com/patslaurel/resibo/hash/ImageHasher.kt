package com.patslaurel.resibo.hash

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * Android bitmap adapter over [PerceptualHash]. Decodes images and extracts pixels.
 *
 * Keep the pure-math core in [PerceptualHash] — this file is the only Android-coupled
 * surface and is covered by instrumentation (not unit) tests.
 */
object ImageHasher {
    /**
     * Decode the image at [uri] and compute its dHash. Returns `null` when the Uri
     * can't be opened, decoded, or describes an unsupported encoding. Never throws;
     * a failed hash degrades the cache-hit story but never breaks the share flow.
     */
    fun dHashFromUri(
        resolver: ContentResolver,
        uri: Uri,
    ): Long? {
        val bitmap = decode(resolver, uri) ?: return null
        return try {
            dHash(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /** Compute dHash over an in-memory bitmap. Caller retains ownership; we do not recycle. */
    fun dHash(bitmap: Bitmap): Long {
        val argb = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return PerceptualHash.dHash(argb, bitmap.width, bitmap.height)
    }

    private fun decode(
        resolver: ContentResolver,
        uri: Uri,
    ): Bitmap? =
        runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
}

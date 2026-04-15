package com.patslaurel.resibo.share

import android.net.Uri

/**
 * Normalized representation of whatever another Android app handed us via ACTION_SEND /
 * ACTION_SEND_MULTIPLE.
 *
 * At T027 scope: text and image URIs only. T029 handles edge cases (video/audio, oversized).
 * T031–T033 feed this into the multimodal normalizer.
 */
data class SharedPost(
    val text: String? = null,
    val imageUris: List<Uri> = emptyList(),
    val mimeType: String? = null,
    val referrer: String? = null,
) {
    val isEmpty: Boolean
        get() = text.isNullOrBlank() && imageUris.isEmpty()

    val kind: Kind
        get() =
            when {
                isEmpty -> Kind.EMPTY
                text != null && imageUris.isNotEmpty() -> Kind.TEXT_AND_IMAGES
                imageUris.size > 1 -> Kind.MULTIPLE_IMAGES
                imageUris.size == 1 -> Kind.SINGLE_IMAGE
                else -> Kind.TEXT_ONLY
            }

    enum class Kind {
        EMPTY,
        TEXT_ONLY,
        SINGLE_IMAGE,
        MULTIPLE_IMAGES,
        TEXT_AND_IMAGES,
    }
}

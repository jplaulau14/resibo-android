package com.patslaurel.resibo.share

import android.net.Uri

/**
 * Normalized representation of whatever another Android app handed us via ACTION_SEND /
 * ACTION_SEND_MULTIPLE.
 *
 * Scope through T029: text + image URIs + video URIs + audio URIs, with warnings for
 * oversized images and URL-only text shares. Downstream (T031–T033) will feed this into
 * the multimodal normalizer (perceptual hash, Whisper transcription, first-frame extract).
 */
data class SharedPost(
    val text: String? = null,
    val imageUris: List<Uri> = emptyList(),
    val videoUris: List<Uri> = emptyList(),
    val audioUris: List<Uri> = emptyList(),
    val mimeType: String? = null,
    val referrer: String? = null,
    val warnings: List<Warning> = emptyList(),
) {
    val isEmpty: Boolean
        get() =
            text.isNullOrBlank() &&
                imageUris.isEmpty() &&
                videoUris.isEmpty() &&
                audioUris.isEmpty()

    val kind: Kind
        get() =
            when {
                isEmpty -> Kind.EMPTY
                text != null && isUrlOnly(text) && imageUris.isEmpty() && videoUris.isEmpty() && audioUris.isEmpty() -> Kind.URL_ONLY
                audioUris.isNotEmpty() -> Kind.AUDIO
                videoUris.isNotEmpty() -> Kind.VIDEO
                text != null && imageUris.isNotEmpty() -> Kind.TEXT_AND_IMAGES
                imageUris.size > 1 -> Kind.MULTIPLE_IMAGES
                imageUris.size == 1 -> Kind.SINGLE_IMAGE
                else -> Kind.TEXT_ONLY
            }

    enum class Kind {
        EMPTY,
        URL_ONLY,
        TEXT_ONLY,
        SINGLE_IMAGE,
        MULTIPLE_IMAGES,
        TEXT_AND_IMAGES,
        VIDEO,
        AUDIO,
    }

    enum class Warning {
        OVERSIZED_IMAGE,
        UNSUPPORTED_MIME,
    }

    companion object {
        /** Treat text as "URL only" when it is a single http(s) URL with no surrounding words. */
        private val URL_ONLY_PATTERN = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)

        fun isUrlOnly(text: String): Boolean = URL_ONLY_PATTERN.matches(text.trim())
    }
}

package com.patslaurel.resibo.hash

/**
 * Difference-hash (dHash) for image near-duplicate detection.
 *
 * Algorithm — resize to [TARGET_WIDTH] × [TARGET_HEIGHT] grayscale, then for each row
 * emit a bit per adjacent-pixel comparison (`left > right → 1`, else 0). That yields
 * [TARGET_HEIGHT] × ([TARGET_WIDTH] - 1) = 64 bits, packed into a [Long].
 *
 * Why dHash rather than aHash/pHash: robust to moderate scaling, recompression, minor
 * brightness shifts, and small crops — the exact distortions Facebook / Viber screenshots
 * pick up as a rumor spreads through family group chats. Implementation is dependency-free
 * (no OpenCV, no JTransforms) and the math is deterministic across Android versions,
 * making it trivial to reproduce the same fingerprint on the train side if needed.
 *
 * Operates on ARGB pixels passed as [IntArray] so the core is unit-testable on pure JVM
 * without Robolectric. Android bitmap callers live in [ImageHasher].
 */
object PerceptualHash {
    const val TARGET_WIDTH = 9
    const val TARGET_HEIGHT = 8
    const val HASH_BITS = TARGET_HEIGHT * (TARGET_WIDTH - 1) // 64

    /**
     * Compute the 64-bit dHash over [argb] (packed ARGB_8888, row-major, length == srcWidth × srcHeight).
     *
     * @throws IllegalArgumentException when the pixel array length doesn't match the stated dimensions.
     */
    fun dHash(
        argb: IntArray,
        srcWidth: Int,
        srcHeight: Int,
    ): Long {
        require(srcWidth > 0 && srcHeight > 0) {
            "srcWidth and srcHeight must be positive (got $srcWidth × $srcHeight)"
        }
        require(argb.size == srcWidth * srcHeight) {
            "argb size ${argb.size} does not match $srcWidth × $srcHeight = ${srcWidth * srcHeight}"
        }

        val gray = resizeAndGrayscale(argb, srcWidth, srcHeight, TARGET_WIDTH, TARGET_HEIGHT)

        var hash = 0L
        for (row in 0 until TARGET_HEIGHT) {
            for (col in 0 until TARGET_WIDTH - 1) {
                val left = gray[row * TARGET_WIDTH + col]
                val right = gray[row * TARGET_WIDTH + col + 1]
                hash = hash shl 1
                if (left > right) hash = hash or 1L
            }
        }
        return hash
    }

    /**
     * Count of differing bits between two dHashes (Hamming distance).
     * Values 0–5 on 64-bit hashes generally indicate near-duplicates.
     */
    fun hammingDistance(
        a: Long,
        b: Long,
    ): Int = java.lang.Long.bitCount(a xor b)

    /** Render hash as 16-char zero-padded hex — convenient for logs and SQLite keys. */
    fun toHex(hash: Long): String = "%016x".format(hash)

    /**
     * Parse a 16-char hex string back into a [Long]. Accepts lowercase or uppercase.
     * @throws IllegalArgumentException for malformed input.
     */
    fun fromHex(hex: String): Long {
        require(hex.length == 16) { "dHash hex must be exactly 16 chars, got '${hex.length}'" }
        return java.lang.Long.parseUnsignedLong(hex, 16)
    }

    /**
     * Nearest-neighbor downsample and ITU-R BT.601 luma conversion in a single pass.
     * Chosen over bilinear/bicubic because the hash collapses to 64 coarse bits anyway
     * — interpolation quality has no measurable effect on near-duplicate detection and
     * wastes CPU cycles on-device.
     */
    private fun resizeAndGrayscale(
        argb: IntArray,
        sw: Int,
        sh: Int,
        dw: Int,
        dh: Int,
    ): IntArray {
        val out = IntArray(dw * dh)
        for (y in 0 until dh) {
            val sy = (y * sh) / dh
            for (x in 0 until dw) {
                val sx = (x * sw) / dw
                val pixel = argb[sy * sw + sx]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                out[y * dw + x] = (r * 299 + g * 587 + b * 114) / 1000
            }
        }
        return out
    }
}

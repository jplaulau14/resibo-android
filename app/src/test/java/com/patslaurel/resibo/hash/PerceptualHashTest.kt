package com.patslaurel.resibo.hash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Pure-JVM unit tests for [PerceptualHash]. Deterministic — no random calls use system time.
 */
class PerceptualHashTest {
    private val dim = 32

    @Test
    fun `identical inputs produce identical hashes`() {
        val pixels = randomArgb(dim, dim, seed = 42)
        val a = PerceptualHash.dHash(pixels, dim, dim)
        val b = PerceptualHash.dHash(pixels, dim, dim)
        assertEquals("dHash must be deterministic", a, b)
    }

    @Test
    fun `distinct random images produce distinct hashes`() {
        val p1 = randomArgb(dim, dim, seed = 1)
        val p2 = randomArgb(dim, dim, seed = 2)
        val h1 = PerceptualHash.dHash(p1, dim, dim)
        val h2 = PerceptualHash.dHash(p2, dim, dim)
        assertNotEquals("Distinct content must hash differently", h1, h2)
    }

    @Test
    fun `minor pixel noise yields small hamming distance`() {
        val original = randomArgb(dim, dim, seed = 7)
        val noisy = addChannelNoise(original, magnitude = 5, seed = 7)
        val distance =
            PerceptualHash.hammingDistance(
                PerceptualHash.dHash(original, dim, dim),
                PerceptualHash.dHash(noisy, dim, dim),
            )
        assertTrue(
            "Expected hamming distance < 10 for ±5 channel noise, got $distance",
            distance < 10,
        )
    }

    @Test
    fun `hamming distance of identical hashes is zero`() {
        assertEquals(0, PerceptualHash.hammingDistance(0xDEADBEEFCAFEBABEUL.toLong(), 0xDEADBEEFCAFEBABEUL.toLong()))
    }

    @Test
    fun `hamming distance of bit-inverted hashes is 64`() {
        assertEquals(64, PerceptualHash.hammingDistance(0L, -1L))
    }

    @Test
    fun `hex round-trip preserves value`() {
        val original = randomArgb(dim, dim, seed = 99)
        val hash = PerceptualHash.dHash(original, dim, dim)
        val hex = PerceptualHash.toHex(hash)
        assertEquals(16, hex.length)
        assertEquals(hash, PerceptualHash.fromHex(hex))
    }

    @Test
    fun `hex always 16 characters even when leading bits are zero`() {
        assertEquals("0000000000000000", PerceptualHash.toHex(0L))
        assertEquals("00000000000000ff", PerceptualHash.toHex(0xFFL))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched pixel array throws`() {
        PerceptualHash.dHash(IntArray(10), dim, dim)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wrong-length hex throws`() {
        PerceptualHash.fromHex("deadbeef")
    }

    private fun randomArgb(
        w: Int,
        h: Int,
        seed: Long,
    ): IntArray {
        val rand = Random(seed)
        return IntArray(w * h) { (0xFF shl 24) or (rand.nextInt(0xFFFFFF)) }
    }

    private fun addChannelNoise(
        pixels: IntArray,
        magnitude: Int,
        seed: Long,
    ): IntArray {
        val rand = Random(seed)
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            val r = noisy((p shr 16) and 0xFF, magnitude, rand)
            val g = noisy((p shr 8) and 0xFF, magnitude, rand)
            val b = noisy(p and 0xFF, magnitude, rand)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun noisy(
        channel: Int,
        magnitude: Int,
        rand: Random,
    ): Int = (channel + rand.nextInt(magnitude * 2 + 1) - magnitude).coerceIn(0, 255)
}

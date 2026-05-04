package com.patslaurel.resibo.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunnerTest {
    @Test
    fun `image-aware claim includes extracted image analysis`() {
        val result =
            buildImageAwareClaim(
                claim = "Fact-check this image.",
                imageAnalysis = "Visible text: Libre na ang MRT sa Mayo 2026.",
            )

        assertTrue(result.contains("Fact-check this image."))
        assertTrue(result.contains("Attached image analysis:"))
        assertTrue(result.contains("Libre na ang MRT sa Mayo 2026."))
        assertFalse(result.contains("[Image attached]"))
    }

    @Test
    fun `build image aware claim trims analysis before composing planner input`() {
        val result =
            buildImageAwareClaim(
                claim = "  Fact-check this image.  ",
                imageAnalysis = "  Visible text: PAGASA Signal No. 5.  ",
            )

        assertEquals(
            "Fact-check this image.\n\nAttached image analysis:\nVisible text: PAGASA Signal No. 5.",
            result,
        )
    }

    @Test
    fun `image-aware claim leaves text-only claims unchanged`() {
        val result =
            buildImageAwareClaim(
                claim = "Totoo ba na libre na ang MRT next month?",
                imageAnalysis = "",
            )

        assertEquals("Totoo ba na libre na ang MRT next month?", result)
    }
}

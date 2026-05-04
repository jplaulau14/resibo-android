package com.patslaurel.resibo.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptLoaderConstantsTest {
    @Test
    fun verificationPlannerConstantUsesBundledAssetName() {
        assertEquals("verification_planner.md", PromptLoader.VERIFICATION_PLANNER)
    }
}

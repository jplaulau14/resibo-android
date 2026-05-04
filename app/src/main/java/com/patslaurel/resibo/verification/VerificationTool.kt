package com.patslaurel.resibo.verification

interface VerificationTool {
    val name: String

    suspend fun execute(call: VerificationToolCall): VerificationToolResult
}

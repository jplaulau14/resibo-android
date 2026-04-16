package com.patslaurel.resibo.ui.check

import android.net.Uri
import com.patslaurel.resibo.agent.ToolResult
import com.patslaurel.resibo.factcheck.FactCheckResult

enum class CheckStep {
    IDLE,
    THINKING,
    TOOL_CALLING,
    GENERATING_NOTE,
    DONE,
    ERROR,
}

data class CheckUiState(
    val inputText: String = "",
    val attachedImageUri: Uri? = null,
    val currentStep: CheckStep = CheckStep.IDLE,
    val activeToolName: String = "",
    val activeToolInput: String = "",
    val toolResults: List<ToolResult> = emptyList(),
    val result: CheckResult? = null,
    val errorMessage: String? = null,
)

data class CheckResult(
    val claim: String,
    val analysis: String,
    val sources: List<FactCheckResult>,
    val responseTimeMs: Long,
    val imageUri: Uri? = null,
    val toolsUsed: List<ToolResult> = emptyList(),
)

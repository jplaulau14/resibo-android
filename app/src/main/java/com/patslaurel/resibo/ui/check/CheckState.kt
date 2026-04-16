package com.patslaurel.resibo.ui.check

import android.net.Uri
import com.patslaurel.resibo.factcheck.FactCheckResult

enum class CheckStep {
    IDLE,
    EXTRACTING_QUERY,
    SEARCHING_WEB,
    GENERATING_NOTE,
    DONE,
    ERROR,
}

data class CheckUiState(
    val inputText: String = "",
    val attachedImageUri: Uri? = null,
    val currentStep: CheckStep = CheckStep.IDLE,
    val searchQuery: String = "",
    val sourceCount: Int = 0,
    val result: CheckResult? = null,
    val errorMessage: String? = null,
)

data class CheckResult(
    val claim: String,
    val analysis: String,
    val sources: List<FactCheckResult>,
    val responseTimeMs: Long,
    val imageUri: Uri? = null,
)

package com.patslaurel.resibo.ui.chat

import android.net.Uri
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String = "",
    val imageUri: Uri? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isGenerating: Boolean = false,
) {
    enum class Role {
        USER,
        RESIBO,
    }
}

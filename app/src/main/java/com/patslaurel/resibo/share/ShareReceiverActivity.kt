package com.patslaurel.resibo.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.patslaurel.resibo.MainActivity
import com.patslaurel.resibo.ui.chat.PendingShare
import dagger.hilt.android.AndroidEntryPoint

/**
 * Thin trampoline: receives ACTION_SEND / ACTION_SEND_MULTIPLE, extracts the shared
 * content, stashes it in [PendingShare], and launches [MainActivity] which will open
 * the Chat tab with the content pre-filled. The user then taps Send to run inference.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val post = intent?.toSharedPost(contentResolver) ?: SharedPost()

        val imageUri = post.imageUris.firstOrNull()
        if (imageUri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    imageUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }

        PendingShare.text = post.text
        PendingShare.imageUri = imageUri

        val mainIntent =
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                imageUri?.let {
                    data = it
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        startActivity(mainIntent)
        finish()
    }
}

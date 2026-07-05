package com.winlator.cmod.feature.stores.steam.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.winlator.cmod.feature.stores.steam.service.SteamService

class ChatImagePickerActivity : ComponentActivity() {
    private val pick =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val friendId = intent.getLongExtra(EXTRA_FRIEND_ID, 0L)
            if (uri != null && friendId != 0L) {
                val cr = contentResolver
                val mime = cr.getType(uri) ?: "image/png"
                val ext = when {
                    mime.contains("jpeg") || mime.contains("jpg") -> "jpeg"
                    mime.contains("gif") -> "gif"
                    mime.contains("webp") -> "webp"
                    else -> "png"
                }
                Thread {
                    runCatching {
                        val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null && bytes.isNotEmpty()) {
                            SteamService.instance?.sendChatImageAsync(friendId, bytes, "image.$ext")
                        }
                    }
                }.start()
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        runCatching {
            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }.onFailure { finish() }
    }

    companion object {
        const val EXTRA_FRIEND_ID = "friendId"
    }
}

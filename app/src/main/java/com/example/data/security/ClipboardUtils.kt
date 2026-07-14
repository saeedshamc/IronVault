package com.example.data.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ClipboardUtils {

    /**
     * Copies text to the clipboard, marking it as sensitive to prevent keyboard history logging.
     * Automatically clears the clipboard after 30 seconds for security.
     */
    fun copyToClipboardSecurely(context: Context, label: String, text: String, clearDelayMs: Long = 30000L) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        
        // Mark as sensitive (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val persistableBundle = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
            clip.description.extras = persistableBundle
        }
        
        clipboardManager.setPrimaryClip(clip)

        // Schedule auto-wipe
        CoroutineScope(Dispatchers.Main).launch {
            delay(clearDelayMs)
            // Verify if clipboard still contains our copied data before clearing (to avoid clearing something newer)
            val currentClip = clipboardManager.primaryClip
            if (currentClip != null && currentClip.itemCount > 0) {
                val currentText = currentClip.getItemAt(0).text?.toString()
                if (currentText == text) {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText(label, ""))
                }
            }
        }
    }
}

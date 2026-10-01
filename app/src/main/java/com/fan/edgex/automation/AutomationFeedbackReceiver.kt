package com.fan.edgex.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.fan.edgex.R
import java.util.Locale

/**
 * Small app-process bridge for actions that are intentionally UI-facing and do not need
 * system_server privileges: toast, speech and local notifications.
 */
class AutomationFeedbackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty().take(MAX_TEXT)
        if (text.isBlank()) return

        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_TOAST -> Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            MODE_TTS -> speak(context.applicationContext, text)
            MODE_NOTIFICATION -> postNotification(context, text)
        }
    }

    private fun speak(context: Context, text: String) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "edgex_automation")
                Handler(Looper.getMainLooper()).postDelayed({ tts?.shutdown() }, 30_000L)
            } else {
                tts?.shutdown()
            }
        }
    }

    private fun postNotification(context: Context, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "EdgeX Automation",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification)
    }

    companion object {
        const val ACTION = "com.fan.edgex.AUTOMATION_FEEDBACK"
        const val EXTRA_MODE = "mode"
        const val EXTRA_TEXT = "text"
        const val MODE_TOAST = "toast"
        const val MODE_TTS = "tts"
        const val MODE_NOTIFICATION = "notification"
        private const val CHANNEL_ID = "automation_feedback"
        private const val MAX_TEXT = 4000
    }
}

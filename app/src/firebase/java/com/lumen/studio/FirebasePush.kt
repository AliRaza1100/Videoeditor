package com.lumen.studio

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Compiled only with -PenableFirebase=true and a real app/google-services.json. */
object FirebasePush {
    @JvmStatic fun configure(context: Context): Boolean {
        if(FirebaseApp.getApps(context).isEmpty()) return false
        val messaging = FirebaseMessaging.getInstance()
        val enabled = Reminders.enabled(context)
        val language = AppPreferences.language(context)
        listOf("en","ur","hi","zh").forEach { code ->
            val topic = "editing-reminders_$code"
            if(enabled && code == language) messaging.subscribeToTopic(topic)
            else messaging.unsubscribeFromTopic(topic)
        }
        return true
    }
}
class LumenMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { FirebasePush.configure(this) }
    override fun onMessageReceived(message: RemoteMessage) {
        // Data-only backend messages allow the local opt-out to suppress display.
        if(message.data["language"] != AppPreferences.language(this)) return
        Reminders.show(this,message.data["title"],message.data["body"],test = false)
    }
}

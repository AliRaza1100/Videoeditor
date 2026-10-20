package com.lumen.studio

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock

object Reminders {
    const val CHANNEL = "editing_reminders"
    const val INTERVAL = 3 * 60 * 60 * 1000L
    private fun prefs(context: Context) = context.getSharedPreferences("reminders",Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled",false)
    fun setEnabled(context: Context,value: Boolean) { prefs(context).edit().putBoolean("enabled",value).apply() }
    private fun pending(context: Context) = PendingIntent.getBroadcast(context,12,Intent(context,ReminderReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun configure(context: Context,force: Boolean = false) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,Ui.t(context,"Editing reminders"),NotificationManager.IMPORTANCE_DEFAULT))
        val remote = try {
            Class.forName("com.lumen.studio.FirebasePush").getMethod("configure",Context::class.java).invoke(null,context) as Boolean
        } catch (_: ReflectiveOperationException) { false }
        val alarm = context.getSystemService(AlarmManager::class.java)
        if(!enabled(context) || remote) {
            alarm.cancel(pending(context)); prefs(context).edit().putBoolean("scheduled",false).apply()
            if(!enabled(context)) manager.cancel(12)
        } else if(force || !prefs(context).getBoolean("scheduled",false)) {
            alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+INTERVAL,INTERVAL,pending(context))
            prefs(context).edit().putBoolean("scheduled",true).apply()
        }
    }
    fun show(context: Context,title: String? = null,body: String? = null,test: Boolean = true) {
        if(!test && !enabled(context)) return
        if(Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,Ui.t(context,"Editing reminders"),NotificationManager.IMPORTANCE_DEFAULT))
        val click = PendingIntent.getActivity(context,12,Intent(context,MainActivity::class.java).putExtra("from_reminder",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(12,Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title ?: "Lumen Studio")
            .setContentText(body ?: Ui.t(context,"Bring your next idea to life. Edit a video or polish a photo in Lumen Studio."))
            .setContentIntent(click).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER).build())
    }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED)
            Reminders.configure(context,true)
        else Reminders.show(context,test = false)
    }
}

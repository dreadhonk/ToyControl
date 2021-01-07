package eu.dreadhonk.apps.toycontrol.control

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import eu.dreadhonk.apps.toycontrol.ControlActivity
import eu.dreadhonk.apps.toycontrol.MainActivity
import eu.dreadhonk.apps.toycontrol.R

class ControlNotification(private var context: Context) {
    companion object {
        private const val CHANNEL_ID = "control_notification"
    }

    private var builder = NotificationCompat.Builder(context, CHANNEL_ID)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.nchannel_title_control)
            val descriptionText = context.getString(R.string.nchannel_description_control)
            val importance = NotificationManager.IMPORTANCE_MIN
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                ContextCompat.getSystemService(context, NotificationManager::class.java)!!
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getDeviceCountStr(deviceCount: Int): String {
        return context.resources.getQuantityString(R.plurals.notification_text_control__devices, deviceCount, deviceCount)
    }

    public fun updateNotification(deviceCount: Int): Notification {
        val pendingIntent: PendingIntent =
            Intent(context, ControlActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(context, 0, notificationIntent, 0)
            }

        val title = context.getString(R.string.notification_title_control)
        builder = builder
            .setContentTitle(title)
            .setContentText(getDeviceCountStr(deviceCount))
            .setSmallIcon(R.drawable.ic_foreground_notification)
            .setContentIntent(pendingIntent)
        return builder.build()
    }

}
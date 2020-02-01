package eu.dreadhonk.apps.toycontrol.control

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import eu.dreadhonk.apps.toycontrol.MainActivity
import eu.dreadhonk.apps.toycontrol.R

class ToyControlService : Service() {
    companion object {
        const val ONGOING_NOTIFICATION_ID = 1;
    }

    class Binder(service: ToyControlService): android.os.Binder() {
        private val service = service

        fun getService(): ToyControlService {
            return service
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return Binder(this)
    }

    override fun onCreate() {
        super.onCreate()

        Log.d("ToyControlService", "onCreate called")
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        val notification = Notification.Builder(this)
            .setContentTitle(getText(R.string.notification_message))
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_foreground_notification)
            .setWhen(0)
            .setPriority(Notification.PRIORITY_MIN);


        startForeground(ONGOING_NOTIFICATION_ID, notification.build())
        Log.d("ToyControlService", "started into foreground")
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

package eu.dreadhonk.apps.toycontrol.control

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothClass
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import eu.dreadhonk.apps.toycontrol.MainActivity
import eu.dreadhonk.apps.toycontrol.R
import eu.dreadhonk.apps.toycontrol.buttplugintf.ButtplugServerFactory
import org.metafetish.buttplug.client.ButtplugClient
import org.metafetish.buttplug.client.ButtplugEmbeddedClient
import org.metafetish.buttplug.core.ButtplugEvent
import org.metafetish.buttplug.core.Messages.DeviceAdded
import org.metafetish.buttplug.core.Messages.DeviceRemoved

class ToyControlService : Service() {
    companion object {
        const val ONGOING_NOTIFICATION_ID = 1;
    }

    public lateinit var client: ButtplugClient;
    private lateinit var controlLoop: Thread;

    class Binder(service: ToyControlService): android.os.Binder() {
        private val service = service

        fun getService(): ToyControlService {
            return service
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return Binder(this)
    }

    private fun makeNotification(msg: CharSequence): Notification.Builder {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        return Notification.Builder(this)
            .setContentTitle(msg)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_foreground_notification)
            .setWhen(0)
            .setPriority(Notification.PRIORITY_MIN);
    }

    override fun onCreate() {
        super.onCreate()

        Log.d("ToyControlService", "onCreate called")

        val notification = makeNotification(getText(R.string.notification_state_disconnected))

        startForeground(ONGOING_NOTIFICATION_ID, notification.build())
        Log.d("ToyControlService", "started into foreground")

        client = ButtplugEmbeddedClient(
            "ToyControl",
            ButtplugServerFactory(this)
        )
        client.initialized.addCallback {
            on_client_initialized(it)
        }
        client.errorReceived.addCallback {
            // TODO: handle errors in some smart way
        }
        client.deviceAdded.addCallback {
            // TODO: handle device addition in some smart way
            Log.d("ToyControlService", "device added: "+(it.message as DeviceAdded).deviceName)
        }
        client.deviceRemoved.addCallback {
            // TODO: handle device removal in some smart way
            Log.d("ToyControlService", "device removed: "+(it.message as DeviceRemoved).deviceIndex)
        }

        val sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        controlLoop = Thread(ControlThread(this, 1000, sensors))
        controlLoop.start()
    }

    fun on_client_initialized(ev: ButtplugEvent) {
        val notification = makeNotification(getText(R.string.notification_state_disconnected))
        NotificationManagerCompat.from(this).notify(ONGOING_NOTIFICATION_ID, notification.build())
        client.startScanning()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun connect() {
        client.connect()
    }
}

package eu.dreadhonk.apps.toycontrol.control

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothClass
import android.content.Context
import android.content.Intent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import eu.dreadhonk.apps.toycontrol.MainActivity
import eu.dreadhonk.apps.toycontrol.R
import eu.dreadhonk.apps.toycontrol.buttplugintf.ButtplugServerFactory
import eu.dreadhonk.apps.toycontrol.data.Device
import eu.dreadhonk.apps.toycontrol.data.DeviceDatabase
import eu.dreadhonk.apps.toycontrol.data.DeviceWithIO
import eu.dreadhonk.apps.toycontrol.data.Provider
import eu.dreadhonk.apps.toycontrol.devices.*
import org.metafetish.buttplug.client.ButtplugClient
import org.metafetish.buttplug.client.ButtplugEmbeddedClient
import org.metafetish.buttplug.core.ButtplugEvent
import org.metafetish.buttplug.core.Messages.DeviceAdded
import org.metafetish.buttplug.core.Messages.DeviceRemoved
import java.util.concurrent.Executors

class ToyControlService : Service() {
    companion object {
        const val ONGOING_NOTIFICATION_ID = 1;
    }

    public lateinit var client: ButtplugClient;
    /* private lateinit var controlLoop: Thread; */
    private lateinit var controller: ToyController
    private lateinit var database: DeviceDatabase
    private lateinit var deviceManager: DeviceManager

    private val deviceListener = object : DeviceManagerCallbacks {
        override fun deviceDeleted(provider: Provider, device: Device) {
            Log.i("ToyControlService", "Device deleted: ${device.displayName} (at ${provider.displayName})")
        }

        override fun deviceOffline(provider: Provider, device: Device) {
            Log.i("ToyControlService", "Device went offline: ${device.displayName} (at ${provider.displayName})")
        }

        override fun deviceOnline(provider: Provider, device: DeviceWithIO) {
            Log.i("ToyControlService", "Device came online: ${device.device.displayName} (at ${provider.displayName})")
        }
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

        database = Room.databaseBuilder(
            applicationContext,
            DeviceDatabase::class.java,
            "toy-database"
        ).build()

        deviceManager = DeviceManager(database)
        deviceManager.listener = deviceListener

        client = ButtplugEmbeddedClient(
            "ToyControl",
            ButtplugServerFactory(this)
        )

        // TODO: think about the thread-safety of this...
        Executors.newSingleThreadExecutor().execute {
            deviceManager.registerProvider(DebugDeviceProvider(), "Test devices")
            val bpProvider = ButtplugDeviceProvider(client)
            bpProvider.initiateScan() // will be queued until connect!
            deviceManager.registerProvider(bpProvider, "Local toys")
        }

        val sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        controller = ToyController(sensors, this, deviceManager)
        controller.start()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun connect() {
        client.connect()
    }
}

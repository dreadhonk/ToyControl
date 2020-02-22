package eu.dreadhonk.apps.toycontrol.control

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothClass
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
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
import java.lang.IllegalStateException
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
            val deviceId = device.device.providerDeviceId
            val providerUri = provider.uri!!
            controller.addDevice(device) {
                val provider = deviceManager.getProviderByUri(providerUri)!!
                provider.setMotors(deviceId, it)
            }
        }
    }

    public interface DeviceEventListener {
        fun deviceOnline(provider: Provider, device: DeviceWithIO)
        fun deviceOffline(provider: Provider, device: Device)
        fun deviceDeleted(provider: Provider, device: Device)
    }

    open class Connection(
        private val context: Context
    ) {
        private var mService: ToyControlService? = null
        private val mConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mService = (service as Binder).connect()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                mService = null
            }
        }

        public val service: ToyControlService
            get() {
                val currService = mService
                if (currService == null) {
                    throw IllegalStateException("not connected to service")
                }

                return currService
            }

        public val isConnected: Boolean
            get() {
                return mService != null
            }

        fun connect() {
            if (mService != null) {
                return
            }
            Intent(context, ToyControlService::class.java).also { intent ->
                context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
            }
        }

        fun disconnect() {
            context.unbindService(mConnection)
            mService = null
        }
    }

    class ScopedConnection(
        private val context: Context,
        private val lifecycle: Lifecycle
    ): LifecycleObserver, Connection(context) {
        init {
            lifecycle.addObserver(this)
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_START)
        fun onStart() {
            connect()
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        fun onStop() {
            disconnect()
        }
    }

    fun getDevices(): Iterable<DeviceWithIO> {
        return database.devices().getAllWithIO()
    }

    class Binder(service: ToyControlService): android.os.Binder() {
        private val service = service

        fun connect(): ToyControlService {
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

        val notification = makeNotification(getText(R.string.notification_text))

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

    fun setSimpleControlMode(deviceId: Long, motor: Int, mode: SimpleControlMode) {
        controller.enableSimpleControl()
        controller.setSimpleControlMode(deviceId, motor, mode)
    }

    fun setManualInputValue(deviceId: Long, motor: Int, value: Float) {
        controller.setManualInput(deviceId, motor, value)
    }


}

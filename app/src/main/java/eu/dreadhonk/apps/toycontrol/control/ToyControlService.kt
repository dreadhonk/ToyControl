package eu.dreadhonk.apps.toycontrol.control

import android.app.*
import android.bluetooth.BluetoothClass
import android.content.*
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.preference.PreferenceManager
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ToyControlService : Service() {
    companion object {
        const val ONGOING_NOTIFICATION_ID = 1;
    }

    private val dbExecutor = Executors.newSingleThreadExecutor()

    public lateinit var client: ButtplugClient;
    /* private lateinit var controlLoop: Thread; */
    private lateinit var controller: ToyController
    private lateinit var database: DeviceDatabase
    private lateinit var deviceManager: DeviceManager

    private lateinit var wakeLock: PowerManager.WakeLock

    private val debugDevices = DebugDeviceProvider()

    private val deviceListener = object : DeviceManagerCallbacks {
        private fun removeDevice(deviceId: Long) {
            controller.removeDevice(deviceId)
            updateDeviceCount()
        }

        private fun updateDeviceCount() {
            controller.post {
                val deviceCount = controller.activeDevices
                Log.d("ToyControlService", String.format("updated device count: %d", deviceCount))
                val handler = Handler(this@ToyControlService.mainLooper)
                handler.post {
                    this@ToyControlService.updateServiceNotification(deviceCount)
                }
            }
        }

        override fun deviceDeleted(provider: Provider, device: Device) {
            Log.i("ToyControlService", "Device deleted: ${device.displayName} (at ${provider.displayName})")
            removeDevice(device.id)
        }

        override fun deviceOffline(provider: Provider, device: Device) {
            Log.i("ToyControlService", "Device went offline: ${device.displayName} (at ${provider.displayName})")
            removeDevice(device.id)
        }

        override fun deviceOnline(provider: Provider, device: DeviceWithIO) {
            val deviceId = device.device.providerDeviceId
            val providerUri = provider.uri!!
            controller.addDevice(device) {
                val provider = deviceManager.getProviderByUri(providerUri)!!
                Log.d("ToyControlService", "Setting device "+deviceId+" motors to "+it)
                provider.setMotors(deviceId, it)
            }
            updateDeviceCount()
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

    private class DebugDeviceController(
            private var provider: DebugDeviceProvider,
            private var context: Context,
            private var dbExecutor: ExecutorService
    ): LifecycleObserver, SharedPreferences.OnSharedPreferenceChangeListener {
        private val PREF_KEY = "debug/enable_dummy_outputs"

        fun setUpListener() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.registerOnSharedPreferenceChangeListener(this)
            val current = prefs.getBoolean(PREF_KEY, false)
            dbExecutor.execute {
                provider.online = current
            }
        }

        fun tearDownListener() {
            PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
            if (key != PREF_KEY) {
                return;
            }
            Log.d("DebugDeviceController", "dummy output preference changed")
            val enabled = sharedPreferences.getBoolean(PREF_KEY, false)
            dbExecutor.execute {
                provider.online = enabled
            }
        }
    }

    private lateinit var debugDeviceController: DebugDeviceController
    private lateinit var notification: ControlNotification

    private fun updateServiceNotification(deviceCount: Int) {
        getSystemService(NotificationManager::class.java)!!.notify(
            ONGOING_NOTIFICATION_ID,
            notification.updateNotification(deviceCount)
        )
    }

    override fun onCreate() {
        super.onCreate()

        Log.d("ToyControlService", "onCreate called")

        notification = ControlNotification(this)
        startForeground(ONGOING_NOTIFICATION_ID, notification.updateNotification(0))
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

        debugDeviceController = DebugDeviceController(
            debugDevices,
            applicationContext,
            dbExecutor
        )
        debugDeviceController.setUpListener()

        // TODO: think about the thread-safety of this...
        Executors.newSingleThreadExecutor().execute {
            deviceManager.registerProvider(debugDevices, "Test devices")
            val bpProvider = ButtplugDeviceProvider(client)
            bpProvider.initiateScan() // will be queued until connect!
            deviceManager.registerProvider(bpProvider, "Local toys")
        }

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ToyControlService::BackgroundControl").apply {
                acquire()
            }
        }

        val sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        controller = ToyController(sensors, this, deviceManager)
        controller.start()
    }

    override fun onDestroy() {
        wakeLock.release()
        debugDeviceController.tearDownListener()
        super.onDestroy()
    }

    fun connect() {
        try {
            client.connect()
        } catch (e: IllegalStateException) {
            Log.i("ToyControlService", "failed to connect: ${e}. ignoring.")
        }
    }

    fun scan() {
        for (provider in deviceManager.providers) {
            provider.initiateScan()
        }
    }

    fun setSimpleControlMode(deviceId: Long, motor: Int, mode: SimpleControlMode) {
        controller.enableSimpleControl()
        controller.setSimpleControlMode(deviceId, motor, mode)
    }

    fun getSimpleControlMode(deviceId: Long, motor: Int): SimpleControlMode? {
        return controller.getSimpleControlMode(deviceId, motor)
    }

    fun setManualInputValue(deviceId: Long, motor: Int, value: Float) {
        controller.setManualInput(deviceId, motor, value)
    }

    fun stop() {
        stopForeground(true)
        stopSelf()
    }
}

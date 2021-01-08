package eu.dreadhonk.apps.toycontrol.service

import android.app.NotificationManager
import android.app.Service
import android.content.*
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.preference.PreferenceManager
import androidx.room.Room
import eu.dreadhonk.apps.toycontrol.buttplugintf.ButtplugServerFactory
import eu.dreadhonk.apps.toycontrol.control.*
import eu.dreadhonk.apps.toycontrol.data.Device
import eu.dreadhonk.apps.toycontrol.data.DeviceDatabase
import eu.dreadhonk.apps.toycontrol.data.DeviceWithIO
import eu.dreadhonk.apps.toycontrol.data.Provider
import eu.dreadhonk.apps.toycontrol.devices.ButtplugDeviceProvider
import eu.dreadhonk.apps.toycontrol.devices.DebugDeviceProvider
import eu.dreadhonk.apps.toycontrol.devices.DeviceManager
import eu.dreadhonk.apps.toycontrol.devices.DeviceManagerCallbacks
import org.metafetish.buttplug.client.ButtplugEmbeddedClient
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.locks.ReentrantLock

/**
 * Foreground service to manage toy controlling
 *
 * Jobs:
 *
 * - manage providers
 * - manage database
 * - hold the control loop
 *
 * It has a single thread where all the important work is serialised. The control loop has its
 * own thread, but its interface is thread-safe, so we do not have to worry about it.
 *
 * Provider callbacks can come from arbitrary threads, so we have to be careful about those.
 */
class ToyControlService : Service() {
    companion object {
        const val ONGOING_NOTIFICATION_ID = 1;
    }

    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var controller: ToyController
    private lateinit var database: DeviceDatabase
    private lateinit var deviceManager: DeviceManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var notification: ControlNotification

    private val remoteListenerLock = ReentrantLock()
    private val remoteListeners = ArrayList<Messenger>()

    private val deviceStateLock = ReentrantLock()
    private val onlineDevices = HashSet<Long>()

    private fun broadcastToListeners(ev: Object) {
        synchronized(remoteListenerLock) {
            val backup = ArrayList<Messenger>(remoteListeners)
            for (listener in backup) {
                try {
                    val msg = Message.obtain()
                    msg.obj = ev
                    listener.send(msg)
                } catch (e: RemoteException) {
                    Log.w("ToyControlService", "listener disappeared, removing")
                    remoteListeners.remove(listener)
                }
            }
        }
    }

    private fun sendSingleMessage(messenger: Messenger, ev: Object) {
        val msg = Message.obtain()
        msg.obj = ev
        messenger.send(msg)
    }

    private fun initialSync(messenger: Messenger) {
        val onlineDevicesCopy = HashSet<Long>()
        synchronized(onlineDevices) {
            onlineDevicesCopy.addAll(onlineDevices)
        }
        val outputs = controller.getAllCurrentOutputs()

        for (device in database.devices().getAllWithIO()) {
            val deviceId = device.device.id
            val isOnline = onlineDevicesCopy.contains(deviceId)
            val type = if (isOnline) DeviceEventType.DEVICE_ONLINE else DeviceEventType.DEVICE_OFFLINE
            Log.v("ToyControlService", "syncing device $deviceId to $messenger (isOnline=$isOnline)")
            sendSingleMessage(messenger, DeviceEvent(type, deviceId, device.device, device.motors.size))
            val currentOutputs = outputs[deviceId]
            if (currentOutputs != null) {
                sendSingleMessage(
                    messenger,
                    DeviceOutputEvent(deviceId, currentOutputs)
                )
            }
        }
    }

    /* Listen for device events to do smart things with them */

    private val deviceEventListener = object : DeviceManagerCallbacks {
        override fun deviceOnline(provider: Provider, device: DeviceWithIO) {
            worker.submit {
                Log.i("ToyControlService", "device ${device.device.providerDeviceId} of ${provider.uri} came online")
                val deviceId = device.device.id
                synchronized(deviceStateLock) {
                    onlineDevices.add(deviceId)
                }
                val provider = deviceManager.getProviderByUri(provider.uri!!)!!
                val providerDeviceId = device.device.providerDeviceId
                controller.addDevice(device) { motors ->
                    Log.d("ToyControlService", "Setting device $providerDeviceId motors to $motors")
                    provider.setMotors(providerDeviceId, motors)
                    broadcastToListeners(DeviceOutputEvent(
                        deviceId,
                        motors
                    ))
                }
                updateNotification()
                broadcastToListeners(DeviceEvent(
                    DeviceEventType.DEVICE_ONLINE,
                    deviceId,
                    device.device,
                    device.motors.size
                ))
            }
        }

        private fun removeOnlineDevice(deviceId: Long) {
            synchronized(deviceStateLock) {
                onlineDevices.remove(deviceId)
            }
            controller.removeDevice(deviceId)
            updateNotification()
        }

        override fun deviceOffline(provider: Provider, device: Device) {
            Log.i("ToyControlService", "device ${device.providerDeviceId} of ${provider.uri} went offline")
            worker.submit {
                val deviceId = device.id
                removeOnlineDevice(deviceId)
                broadcastToListeners(DeviceEvent(
                    DeviceEventType.DEVICE_OFFLINE,
                    deviceId,
                    device,
                    -1
                ))
            }
        }

        override fun deviceDeleted(provider: Provider, device: Device) {
            Log.i("ToyControlService", "device ${device.providerDeviceId} of ${provider.uri} was deleted")
            worker.submit {
                val deviceId = device.id
                removeOnlineDevice(deviceId)
                broadcastToListeners(DeviceEvent(
                    DeviceEventType.DEVICE_DELETED,
                    deviceId,
                    device,
                    -1
                ))
            }
        }
    }

    /* Externally visible classes for use by clients */

    open class Connection(
        private val context: Context,
        receiver: Handler?
    ) {
        private var mService: ToyControlService? = null
        public val messenger: Messenger? = if (receiver == null) null else Messenger(receiver)

        private val mConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val boundService = (service as Binder).connect()
                mService = boundService
                if (messenger != null) {
                    boundService.registerDeviceEventListener(messenger)
                }
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
            val service = mService
            if (messenger != null && service != null) {
                service.unregisterDeviceEventListener(messenger)
            }
            context.unbindService(mConnection)
            mService = null
        }
    }

    class ScopedConnection(
        context: Context,
        receiver: Handler?,
        private val lifecycle: Lifecycle
    ): LifecycleObserver, Connection(context, receiver) {
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

    class Binder(service: ToyControlService): android.os.Binder() {
        private val service = service

        fun connect(): ToyControlService {
            return service
        }
    }

    override fun onBind(intent: Intent): IBinder {
        val messenger = intent.getParcelableExtra<Messenger>("messenger")
        if (messenger != null) {
            registerDeviceEventListener(messenger)
        }
        return Binder(this)
    }

    private fun updateServiceNotification(deviceCount: Int) {
        getSystemService(NotificationManager::class.java)!!.notify(
            ONGOING_NOTIFICATION_ID,
            notification.updateNotification(deviceCount)
        )
    }

    private fun updateNotification() {
        var deviceCount: Int
        synchronized(deviceStateLock) {
            deviceCount = onlineDevices.size
        }

        runOnMainThread {
            updateServiceNotification(deviceCount)
        }
    }

    private fun runOnMainThread(f: () -> Unit) {
        val handler = Handler(this@ToyControlService.mainLooper)
        handler.post { f() }
    }

    private val dummyController = SimplePreferenceProviderController(
        this,
        "debug/enable_dummy_outputs",
        "Dummy outputs",
        DebugDeviceProviderFactory()
    )

    private val buttplugBluetoothController = SimplePreferenceProviderController(
        this,
        "connections/buttplug_bluetooth",
        "Bluetooth",
        ButtplugBluetoothProviderFactory()
    )

    override fun onCreate() {
        super.onCreate()

        Log.d("ToyControlService", "onCreate called")

        notification =
            ControlNotification(this)
        startForeground(ONGOING_NOTIFICATION_ID, notification.updateNotification(0))
        Log.d("ToyControlService", "started into foreground")

        database = Room.databaseBuilder(
            applicationContext,
            DeviceDatabase::class.java,
            "toy-database"
        ).build()

        deviceManager = DeviceManager(database)
        deviceManager.listener = deviceEventListener

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ToyControlService::BackgroundControl").apply {
                acquire()
            }
        }

        val sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        controller = ToyController(
            sensors,
            this,
            deviceManager
        )
        controller.start()

        dummyController.setUpListener()
        buttplugBluetoothController.setUpListener()
    }

    override fun onDestroy() {
        dummyController.tearDownListener()
        buttplugBluetoothController.tearDownListener()
        disconnect()
        worker.shutdownNow()
        wakeLock.release()
        super.onDestroy()
    }

    private fun notifyMissingPermissions(f: () -> Unit) {
        try {
            f()
        } catch (e: PermissionRequired) {
            broadcastToListeners(PermissionRequiredEvent(e.permission))
        }
    }

    fun connect() {
        val task = FutureTask<Unit> {
            for (provider in deviceManager.providers) {
                provider.connect()
            }
        }
        worker.submit(task)
        return task.get()
    }

    fun scan() {
        val task = FutureTask<Unit> {
            for (provider in deviceManager.providers) {
                provider.initiateScan()
            }
        }
        worker.submit(task)
        return task.get()
    }

    fun disconnect() {
        val task = FutureTask<Unit> {
            for (provider in deviceManager.providers) {
                provider.disconnect()
            }
        }
        worker.submit(task)
        return task.get()
    }

    fun addProvider(factory: ProviderFactory, name: String,
                    autoConnect: Boolean = false): String {
        val task = FutureTask<String> {
            val existing = deviceManager.getProviderByUri(factory.uri)
            if (existing != null) {
                return@FutureTask existing.uri
            }

            val provider = factory.createProvider(this)
            deviceManager.registerProvider(provider, name)
            if (autoConnect) {
                provider.connect()
                provider.initiateScan()
            }
            return@FutureTask provider.uri
        }
        worker.submit(task)
        return task.get()
    }

    fun removeProvider(uri: String) {
        worker.submit {
            deviceManager.unregisterProvider(uri)
        }
    }

    fun setSimpleControlMode(deviceId: Long, motor: Int, mode: SimpleControlMode) {
        worker.submit {
            controller.enableSimpleControl()
            controller.setSimpleControlMode(deviceId, motor, mode)
        }
    }

    fun getSimpleControlMode(deviceId: Long, motor: Int): SimpleControlMode? {
        val task = FutureTask<SimpleControlMode?> {
            return@FutureTask controller.getSimpleControlMode(deviceId, motor)
        }
        worker.submit(task)
        return task.get()
    }

    fun setManualInputValue(deviceId: Long, motor: Int, value: Float) {
        worker.submit {
            controller.setManualInput(deviceId, motor, value)
        }
    }

    fun stop() {
        disconnect()
        stopForeground(true)
        stopSelf()
    }

    fun registerDeviceEventListener(messenger: Messenger) {
        synchronized(remoteListenerLock) {
            remoteListeners.add(messenger)
        }
        Log.v("ToyControlService", "queued initial sync to $messenger")
        worker.submit {
            Log.v("ToyControlService", "triggered initial sync to $messenger")
            initialSync(messenger)
        }
    }

    fun unregisterDeviceEventListener(messenger: Messenger) {
        synchronized(remoteListenerLock) {
            remoteListeners.remove(messenger)
        }
    }
}

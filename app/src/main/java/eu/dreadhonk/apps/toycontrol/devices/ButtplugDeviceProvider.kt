package eu.dreadhonk.apps.toycontrol.devices

import android.util.Log
import android.util.LongSparseArray
import androidx.core.util.valueIterator
import org.metafetish.buttplug.client.ButtplugClient
import org.metafetish.buttplug.core.Messages.DeviceAdded
import org.metafetish.buttplug.core.Messages.DeviceRemoved
import org.metafetish.buttplug.core.Messages.VibrateCmd
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

class ButtplugDeviceProvider(
    private val client: ButtplugClient,
    uri: String
): DeviceProvider {
    companion object {
        private val STATE_DISCONNECTED = 0
        private val STATE_SCAN_QUEUED = 1
        private val STATE_CONNECTED = 2
    }

    private val lock = ReentrantLock()
    private val state = AtomicInteger(0)
    private val devices = LongSparseArray<DeviceInfo>()

    override var listener: DeviceProviderCallbackListener? = null
    override val uri = uri

    override fun connect() {
        try {
            client.connect()
        } catch (e: IllegalStateException) {
            Log.i("ButtplugDeviceProvider", "failed to connect: ${e}. ignoring.")
        }
    }

    override fun disconnect() {
        try {
            client.disconnect()
        } catch (e: IllegalStateException) {
            Log.i("ButtplugDeviceProvider", "failed to disconnect: ${e}. ignoring.")
        }
    }

    override fun initiateScan() {
        if (state.compareAndSet(STATE_DISCONNECTED, STATE_SCAN_QUEUED)) {
            Log.i("ButtplugDeviceProvider", "not connected. queueing scan request.")
            return
        }
        if (state.compareAndSet(STATE_SCAN_QUEUED, STATE_SCAN_QUEUED)) {
            Log.i("ButtplugDeviceProvider", "not connected. scan request already pending.")
            return
        }
        Log.i("ButtplugDeviceProvider", "already connected, issuing scan request.")
        client.startScanning()
    }

    override fun devices(): List<DeviceInfo> {
        val result = ArrayList<DeviceInfo>()
        synchronized(lock) {
            for (device in devices.valueIterator()) {
                result.add(device)
            }
        }
        return result
    }

    init {
        client.initialized.addCallback {
            synchronized(lock) {
                handleClientInitialized()
            }
        }
        client.deviceAdded.addCallback {
            synchronized(lock) {
                handleDeviceAdded(it.message as DeviceAdded)
            }
        }
        client.deviceRemoved.addCallback {
            synchronized(lock) {
                handleDeviceRemoved(it.message as DeviceRemoved)
            }
        }
    }

    private fun handleClientInitialized() {
        Log.i("ButtplugDeviceProvider", "client initialized")
        if (!state.compareAndSet(STATE_DISCONNECTED, STATE_CONNECTED)) {
            // previous state was not DISCONNECTED
            if (state.compareAndSet(STATE_SCAN_QUEUED, STATE_CONNECTED)) {
                Log.i("ButtplugDeviceProvider", "initiating queued scan")
                client.startScanning()
            } else {
                throw RuntimeException("unexpected state: ${state.get()}")
            }
        }
    }

    private fun handleDeviceAdded(msg: DeviceAdded) {
        var motorCount = msg.deviceMessages["VibrateCmd"]?.featureCount
        if (motorCount == null) {
            // check if single vibrate is supported, then assume one
            if (msg.deviceMessages.containsKey("SingleMotorVibrateCmd")) {
                motorCount = 1
            } else {
                motorCount = 0
            }
        }

        val motorInfo = Array<MotorInfo>(motorCount.toInt()) {
            // FIXME: use the correct step count here. AFAIK, buttplug-android does not support
            // discovering that, so we'll have to fake it in some way. or make buttplug-android
            // read the new device json stuff.
            MotorInfo(steps=20)
        }

        val info = DeviceInfo(
            providerDeviceId=msg.deviceIndex,
            displayName=msg.deviceName,
            motors=motorInfo
        )
        Log.d("ButtplugDeviceProvider", "Device added: ${info.displayName} with ID ${info.providerDeviceId}. discovered ${info.motors.size} motors")
        devices.put(msg.deviceIndex, info)
        val listener = this.listener
        if (listener != null) {
            listener.deviceOnline(this, info)
        }
    }

    private fun handleDeviceRemoved(msg: DeviceRemoved) {
        devices.remove(msg.deviceIndex)
        Log.d("ButtplugDeviceProvider", "Device removed with ID ${msg.deviceIndex}")
        val listener = this.listener
        if (listener != null) {
            listener.deviceOffline(this, msg.deviceIndex)
        }
    }

    override fun setMotor(providerDeviceId: Long, motorIndex: Int, value: Float) {
        val subcommands = ArrayList<VibrateCmd.VibrateSubcommand>();
        val command = VibrateCmd(providerDeviceId, subcommands, client.nextMsgId)
        subcommands.add(command.VibrateSubcommand(motorIndex.toLong(), value.toDouble()))
        client.sendDeviceMessage(providerDeviceId, command)
    }

    override fun setMotors(providerDeviceId: Long, values: FloatArray) {
        val subcommands = ArrayList<VibrateCmd.VibrateSubcommand>();
        val command = VibrateCmd(providerDeviceId, subcommands, client.nextMsgId)
        values.forEachIndexed { index, value ->
            subcommands.add(command.VibrateSubcommand(index.toLong(), value.toDouble()))
        }
        client.sendDeviceMessage(providerDeviceId, command)
    }
}
package eu.dreadhonk.apps.toycontrol.devices

import android.util.Log
import android.util.LongSparseArray
import androidx.core.util.forEach
import androidx.core.util.valueIterator
import org.metafetish.buttplug.client.ButtplugClient
import org.metafetish.buttplug.core.IButtplugDevice
import org.metafetish.buttplug.core.Messages.DeviceAdded
import org.metafetish.buttplug.core.Messages.DeviceRemoved
import org.metafetish.buttplug.core.Messages.SingleMotorVibrateCmd
import org.metafetish.buttplug.core.Messages.VibrateCmd
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ButtplugDeviceProvider(client: ButtplugClient): DeviceProvider {
    companion object {
        private val STATE_DISCONNECTED = 0
        private val STATE_SCAN_QUEUED = 1
        private val STATE_CONNECTED = 1
    }

    private val client = client
    private val state = AtomicInteger(0)
    private val devices = LongSparseArray<DeviceInfo>()

    override var listener: DeviceProviderCallbackListener? = null
    override val online: Boolean
        get() = state.get() == STATE_CONNECTED
    override val uri: String
        get() = "buttplug:"

    override fun initiateScan() {
        if (state.compareAndSet(STATE_DISCONNECTED, STATE_SCAN_QUEUED)) {
            Log.i("ButtplugDeviceProvider", "not connected. queueing scan request.")
            return
        }
        if (state.compareAndSet(STATE_SCAN_QUEUED, STATE_SCAN_QUEUED)) {
            Log.i("ButtplugDeviceProvider", "not connected. queueing scan request.")
            return
        }
        Log.i("ButtplugDeviceProvider", "not connected. queueing scan request.")
        client.startScanning()
    }

    override fun devices(): Iterator<DeviceInfo> {
        return devices.valueIterator()
    }

    init {
        client.initialized.addCallback {
            handleClientInitialized()
        }
        client.deviceAdded.addCallback {
            handleDeviceAdded(it.message as DeviceAdded)
        }
        client.deviceRemoved.addCallback {
            handleDeviceRemoved(it.message as DeviceRemoved)
        }
    }

    private fun handleClientInitialized() {
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
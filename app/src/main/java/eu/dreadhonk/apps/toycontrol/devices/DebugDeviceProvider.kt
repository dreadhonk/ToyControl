package eu.dreadhonk.apps.toycontrol.devices

import android.util.Log

class DebugDeviceProvider(override public val uri: String): DeviceProvider {
    private val debugDevice = DeviceInfo(
        displayName="Test device",
        providerDeviceId=1,
        motors=arrayOf(
            MotorInfo(steps=20),
            MotorInfo(steps=20)
        )
    )

    override var listener: DeviceProviderCallbackListener? = null

    private var connected: Boolean = false

    override fun devices(): List<DeviceInfo> {
        if (!connected) {
            return ArrayList<DeviceInfo>()
        }
        return ArrayList(listOf(debugDevice))
    }

    override fun connect() {
        val was_connected = connected
        connected = true
        if (!was_connected) {
            listener?.deviceOnline(this, debugDevice)
        }
    }

    override fun disconnect() {
        val was_connected = connected
        connected = false
        if (was_connected) {
            listener?.deviceDeleted(this, debugDevice.providerDeviceId)
        }
    }

    override fun initiateScan() {
        // scan is a no-op for debug devices
    }

    override fun setMotor(providerDeviceId: Long, motorIndex: Int, value: Float) {
        Log.d("DebugDeviceProvider", "device ${providerDeviceId} motor ${motorIndex} set to ${value}")
    }

    override fun setMotors(providerDeviceId: Long, values: FloatArray) {
        Log.d("DebugDeviceProvider", "device ${providerDeviceId} motors set to ${values}")
    }
}
package eu.dreadhonk.apps.toycontrol.devices

import android.util.Log

class DebugDeviceProvider: DeviceProvider {
    private val debugDevice = DeviceInfo(
        displayName="Test device",
        providerDeviceId=1,
        motors=arrayOf(
            MotorInfo(steps=20),
            MotorInfo(steps=20),
            MotorInfo(steps=20),
            MotorInfo(steps=20)
        )
    )

    override var listener: DeviceProviderCallbackListener? = null
    override val online: Boolean = true
    override val uri: String = "local:"

    override fun devices(): Iterator<DeviceInfo> {
        return listOf(debugDevice).listIterator()
    }

    init {
        this.listener?.deviceOnline(this, debugDevice)
    }

    override fun initiateScan() {
    }

    override fun setMotor(providerDeviceId: Long, motorIndex: Int, value: Float) {
        Log.d("DebugDeviceProvider", "device ${providerDeviceId} motor ${motorIndex} set to ${value}")
    }

    override fun setMotors(providerDeviceId: Long, values: FloatArray) {
        Log.d("DebugDeviceProvider", "device ${providerDeviceId} motors set to ${values}")
    }
}
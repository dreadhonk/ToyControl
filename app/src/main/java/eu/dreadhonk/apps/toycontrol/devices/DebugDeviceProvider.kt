package eu.dreadhonk.apps.toycontrol.devices

import android.util.Log

class DebugDeviceProvider: DeviceProvider {
    private val debugDevice = DeviceInfo(
        displayName="Test device",
        providerDeviceId=1,
        motors=arrayOf(
            MotorInfo(steps=20),
            MotorInfo(steps=20)
        )
    )

    private var mOnlineStatus: Boolean = false

    override var listener: DeviceProviderCallbackListener? = null
    override var online: Boolean
        get() {
            return mOnlineStatus;
        }
        set(new_value) {
            val old = mOnlineStatus
            mOnlineStatus = new_value
            if (old != new_value) {
                updateOnlineStatus()
            }
        }
    override val uri: String = "local:"

    override fun devices(): Iterator<DeviceInfo> {
        return listOf(debugDevice).listIterator()
    }

    private fun updateOnlineStatus() {
        if (mOnlineStatus) {
            this.listener?.deviceOnline(this, debugDevice)
        } else {
            this.listener?.deviceDeleted(this, debugDevice.providerDeviceId)
        }
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
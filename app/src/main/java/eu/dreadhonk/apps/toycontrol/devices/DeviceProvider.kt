package eu.dreadhonk.apps.toycontrol.devices

interface DeviceProvider {
    public var listener: DeviceProviderCallbackListener?
    public val uri: String
    public val online: Boolean

    fun devices(): Iterator<DeviceInfo>
    fun initiateScan()

    fun setMotor(providerDeviceId: Long, motorIndex: Int, value: Float)

    fun setMotors(providerDeviceId: Long, values: FloatArray) {
        for (i in values.indices) {
            val value = values[i]
            setMotor(providerDeviceId, i, value)
        }
    }
}

interface DeviceProviderCallbackListener {
    fun deviceOnline(provider: DeviceProvider, device: DeviceInfo)
    fun deviceOffline(provider: DeviceProvider, deviceId: Long)
    fun deviceDeleted(provider: DeviceProvider, deviceId: Long)
}
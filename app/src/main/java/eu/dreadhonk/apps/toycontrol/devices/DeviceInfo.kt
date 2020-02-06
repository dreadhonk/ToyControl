package eu.dreadhonk.apps.toycontrol.devices

data class MotorInfo(
    public val steps: Long
)

data class DeviceInfo(
    public val providerDeviceId: Long,
    public val displayName: String,
    public val motors: Array<MotorInfo>
)
package eu.dreadhonk.apps.toycontrol.control

enum class DeviceEventType(val id: Int) {
    DEVICE_ONLINE(1),
    DEVICE_OFFLINE(2),
    DEVICE_DELETED(3)
}

class DeviceEvent(
    val type: DeviceEventType,
    val deviceId: Long
): Object() {}

class DeviceOutputEvent(
    val deviceId: Long,
    val motors: FloatArray
): Object() {}
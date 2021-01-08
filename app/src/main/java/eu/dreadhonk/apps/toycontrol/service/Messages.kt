package eu.dreadhonk.apps.toycontrol.service

import eu.dreadhonk.apps.toycontrol.data.Device

enum class DeviceEventType(val id: Int) {
    DEVICE_ONLINE(1),
    DEVICE_OFFLINE(2),
    DEVICE_DELETED(3)
}

class DeviceEvent(
    val type: DeviceEventType,
    val deviceId: Long,
    val device: Device,
    val motors: Int
): Object() {}

class DeviceOutputEvent(
    val deviceId: Long,
    val motors: FloatArray
): Object() {}

class PermissionRequiredEvent(
    val permission: String
): Object() {}
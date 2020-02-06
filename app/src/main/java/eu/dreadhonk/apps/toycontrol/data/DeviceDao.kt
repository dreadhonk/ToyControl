package eu.dreadhonk.apps.toycontrol.data

import androidx.room.*
import eu.dreadhonk.apps.toycontrol.devices.DeviceInfo

@Dao
abstract class DeviceDao {
    @Delete
    abstract fun delete(device: Device)

    @Insert
    abstract fun insertAll(vararg devices: Device)

    @Query("SELECT * FROM device")
    abstract fun getAll(): List<Device>

    @Query("SELECT * FROM device WHERE id = :deviceId")
    abstract fun getByID(deviceId: Long): Device?

    @Query("SELECT * FROM device WHERE providerId = :providerId AND providerDeviceId = :providerDeviceId")
    abstract fun getByIDs(providerId: Long, providerDeviceId: Long): Device?

    @Transaction
    @Query("SELECT * FROM device")
    abstract fun getAllWithIO(): List<DeviceWithIO>

    @Transaction
    @Query("SELECT * FROM device WHERE providerId = :providerId AND providerDeviceId = :providerDeviceId")
    abstract fun getWithIOByIDs(providerId: Long, providerDeviceId: Long): DeviceWithIO?

    @Insert
    abstract protected fun insertAllMotors(vararg motors: Motor)

    @Delete
    abstract protected fun deleteMotor(motor: Motor)

    @Update
    abstract protected fun updateMotor(motor: Motor)

    @Query("DELETE FROM device WHERE providerId = :providerId AND providerDeviceId = :providerDeviceId")
    abstract fun deleteByIDs(providerId: Long, providerDeviceId: Long)

    @Update
    abstract fun update(device: Device)

    @Transaction
    open fun deepUpdateFromInfo(providerId: Long, device: DeviceInfo): DeviceWithIO {
        val existing = getWithIOByIDs(providerId, device.providerDeviceId)
        if (existing == null) {
            // easy case
            // insert the device first
            insertAll(Device(id=0, displayName=device.displayName, providerId=providerId, providerDeviceId=device.providerDeviceId))
            val newDevice = getByIDs(providerId, device.providerDeviceId)
            if (newDevice == null) {
                throw RuntimeException("insertion of device (hah) did not create a new row")
            }
            for (motor in device.motors) {
                insertAllMotors(Motor(deviceId=newDevice.id, steps=motor.steps))
            }
        } else {
            // more tricky case
            if (device.motors.size != existing.motors.size) {
                // changing the number of motors means this is a new device, actually; it would
                // otherwise invalidate graphs.
                // so we delete the old device and replace it with the new one.
                delete(existing.device)
                // re-invoking replace should return null for the initial check -> cause an insert
                return deepUpdateFromInfo(providerId, device)
            }

            // number of motors matches, we can do updates
            update(existing.device.copy(displayName=device.displayName))
            existing.motors.zip(device.motors) { old, new ->
                updateMotor(old.copy(steps=new.steps))
            }
        }
        return getWithIOByIDs(providerId, device.providerDeviceId)!!
    }
}
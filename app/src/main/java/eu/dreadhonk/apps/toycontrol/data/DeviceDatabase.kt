package eu.dreadhonk.apps.toycontrol.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = arrayOf(Device::class, Provider::class, Motor::class), version = 1)
abstract class DeviceDatabase: RoomDatabase() {
    abstract fun devices(): DeviceDao
    abstract fun providers(): ProviderDao
}
package eu.dreadhonk.apps.toycontrol.data

import androidx.room.*

@Entity(
    indices=[Index(value=["uri"], unique=true)]
)
data class Provider(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "uri") val uri: String?,
    @ColumnInfo(name = "display_name") val displayName: String?
)

@Entity(
    foreignKeys=[ForeignKey(
        entity=Provider::class,
        parentColumns=["id"],
        childColumns=["providerId"],
        onDelete=ForeignKey.CASCADE,
        onUpdate=ForeignKey.CASCADE
    )],
    indices=[
        Index(value=["providerId", "providerDeviceId"], unique=true)
    ]
)
data class Device(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "providerId") val providerId: Long,
    @ColumnInfo(name = "providerDeviceId") val providerDeviceId: Long
)

@Entity(
    foreignKeys=[ForeignKey(
        entity=Device::class,
        parentColumns=["id"],
        childColumns=["deviceId"],
        onDelete=ForeignKey.CASCADE,
        onUpdate=ForeignKey.CASCADE)]
)
data class Motor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "deviceId") val deviceId: Long,
    @ColumnInfo(name = "steps") val steps: Long
)

data class ProviderWithDevices(
    @Embedded val provider: Provider,
    @Relation(
        parentColumn = "id",
        entityColumn = "providerId"
    )
    val devices: List<Device>
)

data class DeviceWithIO(
    @Embedded val device: Device,
    @Relation(
        parentColumn = "id",
        entityColumn = "deviceId"
    )
    val motors: List<Motor>
)
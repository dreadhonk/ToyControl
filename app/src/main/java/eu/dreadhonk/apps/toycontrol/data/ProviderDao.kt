package eu.dreadhonk.apps.toycontrol.data

import androidx.room.*

@Dao
interface ProviderDao {
    @Query("SELECT * FROM provider")
    fun getAll(): List<Provider>

    @Query("SELECT * FROM provider WHERE id = :id")
    fun getByID(id: Long): Provider?

    @Query("SELECT * FROM provider WHERE uri = :uri")
    fun getByURI(uri: String): Provider?

    @Delete
    fun delete(provider: Provider)

    @Insert
    fun insertAll(vararg providers: Provider)

    @Update
    fun update(provider: Provider)

    @Transaction
    @Query("SELECT * FROM provider")
    fun getAllWithDevices(): List<ProviderWithDevices>

    @Transaction
    fun requireProvider(uri: String): Provider {
        val existing = getByURI(uri)
        if (existing == null) {
            insertAll(Provider(uri=uri, displayName=null))
            return getByURI(uri)!!
        }
        return existing
    }

    @Transaction
    @Query("SELECT * FROM provider WHERE uri = :uri")
    fun getWithDevicesByURI(uri: String): ProviderWithDevices?
}
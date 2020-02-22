package eu.dreadhonk.apps.toycontrol.devices

import eu.dreadhonk.apps.toycontrol.data.Device
import eu.dreadhonk.apps.toycontrol.data.DeviceDatabase
import eu.dreadhonk.apps.toycontrol.data.DeviceWithIO
import eu.dreadhonk.apps.toycontrol.data.Provider

interface DeviceManagerCallbacks {
    fun deviceOffline(provider: Provider, device: Device)
    fun deviceOnline(provider: Provider, device: DeviceWithIO)
    fun deviceDeleted(provider: Provider, device: Device)
}

class DeviceManager(database: DeviceDatabase) {
    public var listener: DeviceManagerCallbacks? = null
    private val database: DeviceDatabase = database

    inner class ProviderListener: DeviceProviderCallbackListener {
        override fun deviceOffline(provider: DeviceProvider, deviceId: Long) {
            handleDeviceOffline(provider, deviceId)
        }

        override fun deviceOnline(provider: DeviceProvider, device: DeviceInfo) {
            handleDeviceOnline(provider, device)
        }

        override fun deviceDeleted(provider: DeviceProvider, deviceId: Long) {
            handleDeviceDeleted(provider, deviceId)
        }
    }

    private val ownListener = ProviderListener()
    private val mProviders = HashMap<String, DeviceProvider>()

    public val providers: List<DeviceProvider>
        get() {
            return mProviders.values.asSequence().toList()
        }

    public fun getProviderByUri(uri: String): DeviceProvider? {
        return mProviders[uri]
    }

    private fun handleDeviceOnline(provider: DeviceProvider, device: DeviceInfo) {
        val localProvider = database.providers().getByURI(provider.uri)!!
        val localDevice = database.devices().deepUpdateFromInfo(localProvider.id, device)
        val listener = this.listener
        if (listener == null) {
            return
        }
        listener.deviceOnline(localProvider, localDevice)
    }

    private fun handleDeviceOffline(provider: DeviceProvider, deviceId: Long) {
        // TODO: record "last seen" in the database?
        val listener = this.listener
        if (listener == null) {
            return
        }
        val localProvider = database.providers().getByURI(provider.uri)!!
        val localDevice = database.devices().getByIDs(localProvider.id, deviceId)!!
        listener.deviceOffline(localProvider, localDevice)
    }

    private fun handleDeviceDeleted(provider: DeviceProvider, deviceId: Long) {
        val localProvider = database.providers().getByURI(provider.uri)!!
        database.devices().deleteByIDs(localProvider.id, deviceId)
        val listener = this.listener
        if (listener == null) {
            return
        }
        val localDevice = database.devices().getByIDs(localProvider.id, deviceId)!!
        listener.deviceDeleted(localProvider, localDevice)
    }

    fun registerProvider(provider: DeviceProvider, defaultDisplayName: String? = null) {
        mProviders[provider.uri] = provider
        database.runInTransaction {
            val dao = database.providers()
            val provider = dao.requireProvider(provider.uri)
            if (provider.displayName == null && defaultDisplayName != null) {
                dao.update(provider.copy(displayName=defaultDisplayName))
            }
        }

        if (provider.online) {
            for (device in provider.devices()) {
                handleDeviceOnline(provider, device)
            }
        }
        provider.listener = ownListener
    }

    fun deleteProvider(provider: DeviceProvider) {
        val listener = this.listener
        if (mProviders.remove(provider.uri) != null) {
            val dao = database.providers()
            var localProvider: Provider
            if (listener != null) {
                val info = dao.getWithDevicesByURI(provider.uri)!!
                localProvider = info.provider
                for (device in info.devices) {
                    listener.deviceOffline(localProvider, device)
                    listener.deviceDeleted(localProvider, device)
                }
            } else {
                localProvider = dao.getByURI(provider.uri)!!
            }
            provider.listener = null
            dao.delete(localProvider)
        }
    }
}
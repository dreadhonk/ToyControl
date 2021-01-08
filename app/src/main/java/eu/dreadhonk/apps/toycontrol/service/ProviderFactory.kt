package eu.dreadhonk.apps.toycontrol.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import eu.dreadhonk.apps.toycontrol.buttplugintf.ButtplugServerFactory
import eu.dreadhonk.apps.toycontrol.devices.ButtplugDeviceProvider
import eu.dreadhonk.apps.toycontrol.devices.DebugDeviceProvider
import eu.dreadhonk.apps.toycontrol.devices.DeviceProvider
import org.metafetish.buttplug.client.ButtplugEmbeddedClient

interface ProviderFactory {
    val uri: String

    fun createProvider(context: Context): DeviceProvider
}

class ButtplugBluetoothProviderFactory: ProviderFactory {
    override val uri = "buttplug:bluetooth"

    override fun createProvider(context: Context): DeviceProvider {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw PermissionRequired(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        return ButtplugDeviceProvider(
            ButtplugEmbeddedClient(
                "ToyControl",
                ButtplugServerFactory(context)
            ),
            uri
        )
    }
}

class DebugDeviceProviderFactory: ProviderFactory {
    override val uri = "dummy:"  // FIXME: this is only here because my device still has it

    override fun createProvider(context: Context): DeviceProvider {
         return DebugDeviceProvider(uri)
    }
}
package eu.dreadhonk.apps.toycontrol.buttplugintf

import android.content.Context
import org.metafetish.buttplug.server.ButtplugServer
import org.metafetish.buttplug.server.IButtplugServerFactory
import org.metafetish.buttplug.server.managers.androidbluetoothmanager.AndroidBluetoothManager

class ButtplugServerFactory(context: Context): IButtplugServerFactory {
    private val context = context;

    override fun getServer(): ButtplugServer {
        val srv = ButtplugServer("embedded");
        srv.addDeviceSubtypeManager(AndroidBluetoothManager(context))
        return srv
    }
}
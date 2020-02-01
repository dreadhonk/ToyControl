package eu.dreadhonk.apps.toycontrol

import android.content.Intent
import eu.dreadhonk.apps.toycontrol.buttplugintf.ButtplugServerFactory
import eu.dreadhonk.apps.toycontrol.control.ToyControlService
import org.metafetish.buttplug.client.ButtplugClient
import org.metafetish.buttplug.client.ButtplugEmbeddedClient

class Application: android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        Intent(this, ToyControlService::class.java).also { intent ->
            startService(intent)
        }
    }
}
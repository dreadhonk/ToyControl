package eu.dreadhonk.apps.toycontrol

import android.content.Intent
import eu.dreadhonk.apps.toycontrol.control.ToyControlService

class Application: android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        Intent(this, ToyControlService::class.java).also { intent ->
            startService(intent)
        }
    }
}
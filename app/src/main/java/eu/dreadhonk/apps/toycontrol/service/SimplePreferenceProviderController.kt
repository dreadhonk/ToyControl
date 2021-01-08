package eu.dreadhonk.apps.toycontrol.service

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager

class SimplePreferenceProviderController(
    private val service: ToyControlService,
    private val prefKey: String,
    private val name: String,
    private val factory: ProviderFactory
): SharedPreferences.OnSharedPreferenceChangeListener {
    fun setUpListener() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(service)
        prefs.registerOnSharedPreferenceChangeListener(this)
        val current = prefs.getBoolean(prefKey, false)
        updateState(current)
    }

    fun tearDownListener() {
        PreferenceManager.getDefaultSharedPreferences(
            service
        ).unregisterOnSharedPreferenceChangeListener(
            this
        )
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key != prefKey) {
            return;
        }
        Log.d("SimplePreferenceProviderController",
            "preference $prefKey changed for $factory")
        val enabled = sharedPreferences.getBoolean(prefKey, false)
        updateState(enabled)
    }

    private fun updateState(enabled: Boolean) {
        if (enabled) {
            service.addProvider(factory, name, autoConnect = true)
        } else {
            service.removeProvider(factory.uri)
        }
    }
}

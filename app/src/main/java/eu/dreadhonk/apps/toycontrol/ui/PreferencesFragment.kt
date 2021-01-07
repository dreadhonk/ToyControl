package eu.dreadhonk.apps.toycontrol.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import eu.dreadhonk.apps.toycontrol.R

class PreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs, rootKey)
    }
}
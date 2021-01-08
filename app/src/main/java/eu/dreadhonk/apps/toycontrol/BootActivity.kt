package eu.dreadhonk.apps.toycontrol

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.edit
import androidx.preference.PreferenceManager

class BootActivity : AppCompatActivity() {
    companion object {
        val SETUP_COMPLETE_PREF_KEY = "meta/setup_complete"
        val SETUP_COMPLETE_DEFAULT = 0
        val SETUP_COMPLETE_VALUE = 1

        fun markFirstRunDone(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit {
                putBoolean("connections/buttplug_bluetooth", true)
                putInt(SETUP_COMPLETE_PREF_KEY, SETUP_COMPLETE_VALUE)
            }
        }
    }

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onResume() {
        super.onResume()

        if (prefs.getInt(SETUP_COMPLETE_PREF_KEY, SETUP_COMPLETE_DEFAULT) != SETUP_COMPLETE_VALUE) {
            // first setup needed -> go there
            startActivity(Intent(this, FirstRunActivity::class.java))
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
package eu.dreadhonk.apps.toycontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager

class FirstRunActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_first_run)

        findViewById<Button>(R.id.btn_next).apply {
            setOnClickListener { btn ->
                requestPermissionAndMoveOn()
            }
        }
    }

    private fun requestPermissionAndMoveOn() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("FirstRunActivity", "fine location access not granted -> requesting")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MainActivity.REQUEST_LOCATION
            )
            return;
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            MainActivity.REQUEST_LOCATION -> if (grantResults.size != 0) {
                // permissions granted, force connect ...
                Log.d("FirstRunActivity", "fine location access granted -> moving on")
                BootActivity.markFirstRunDone(this)
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                Log.d("FirstRunActivity", "fine location access not granted -> ???")
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }

    }
}
package eu.dreadhonk.apps.toycontrol

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.text.Layout
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import eu.dreadhonk.apps.toycontrol.control.ToyControlService
import org.metafetish.buttplug.client.ButtplugClient
import org.metafetish.buttplug.core.ButtplugEvent
import org.metafetish.buttplug.core.Messages.DeviceAdded
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object {
        const val REQUEST_LOCATION = 1;
    }

    private val conn = ToyControlService.ScopedConnection(this, this.lifecycle)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_connect).also {
            it.setOnClickListener {
                connect()
            }
        }

        findViewById<Button>(R.id.btn_scan).also {
            it.setOnClickListener {
                scan()
            }
        }

        findViewById<Button>(R.id.btn_control).also {
            it.setOnClickListener {
                val intent = Intent(this, ControlActivity::class.java)
                startActivity(intent)
            }
        }

        /* val glview = SurfaceView(this)
        findViewById<LinearLayout>(R.id.main_layout).also {
            it.addView(glview, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } */
    }

    fun connect() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("MainActivity", "fine location access not granted -> requesting")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION
            )
            return;
        }
        Log.d("MainActivity", "fine location access granted -> proceeding with connection")
        do_connect()
    }

    fun scan() {
        conn.service.scan()
    }

    private fun do_connect() {
        conn.service.connect()
    }

    private fun handle_connected(client: ButtplugClient) {
        client.startScanning()
        runOnUiThread {
            Toast.makeText(this, "Server ready", 10).show()
        }
    }

    private fun handle_device(ev: ButtplugEvent) {
        val msg = ev.message
        if (msg is DeviceAdded) {
            val name = msg.deviceName
            Toast.makeText(this, "Device added: " + name, 10).show()
        } else {
            val label = msg.toString()
            Toast.makeText(this, "Unexpected message: " + label, 10).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_LOCATION -> if (grantResults.size != 0) {
                // permissions granted, force connect ...
                Log.d("MainActivity", "fine location access granted -> connecting now")
                do_connect()
            } else {
                Log.d("MainActivity", "fine location access not granted -> ???")
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }

    }
}

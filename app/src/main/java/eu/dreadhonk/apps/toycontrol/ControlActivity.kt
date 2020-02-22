package eu.dreadhonk.apps.toycontrol

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import eu.dreadhonk.apps.toycontrol.control.MathUtil
import eu.dreadhonk.apps.toycontrol.control.SimpleControlMode
import eu.dreadhonk.apps.toycontrol.control.ToyControlService
import eu.dreadhonk.apps.toycontrol.data.Device
import eu.dreadhonk.apps.toycontrol.data.DeviceWithIO
import eu.dreadhonk.apps.toycontrol.ui.IntensityControl
import eu.dreadhonk.apps.toycontrol.ui.ValueView
import java.util.concurrent.Executors

class ControlActivity : AppCompatActivity() {
    private val controls = ArrayList<IntensityControl>()
    private lateinit var targetLayout: LinearLayout
    private var mService: ToyControlService? = null

    private val mConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val remoteService = (service as ToyControlService.Binder).connect()
            mService = remoteService
            Executors.newSingleThreadExecutor().execute {
                val devices = remoteService.getDevices()
                runOnUiThread {
                    refreshDevices(devices)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)
        targetLayout = findViewById<LinearLayout>(R.id.control_layout)
    }

    private class EventForwarder(
        private val service: ToyControlService,
        private val deviceId: Long,
        private val motor: Int
    ): IntensityControl.IntensityControlListener {

        override fun onManualValueChange(newValue: Float) {
            service.setManualInputValue(deviceId, motor, newValue)
        }

        override fun onModeChange(newMode: SimpleControlMode) {
            service.setSimpleControlMode(deviceId, motor, newMode)
        }
    }

    private fun refreshDevices(devices: Iterable<DeviceWithIO>) {
        targetLayout.removeAllViews()
        controls.clear()

        for (device in devices) {
            for (output in 0 until device.motors.size) {
                val newControl = IntensityControl(this)
                newControl.deviceName = device.device.displayName
                newControl.devicePort = "M${output}"
                newControl.listener = EventForwarder(mService!!, device.device.id, output)
                controls.add(newControl)
                targetLayout.addView(
                    newControl,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1.0f
                    )
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, ToyControlService::class.java).also { intent ->
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        targetLayout.removeAllViews()
        controls.clear()
        unbindService(mConnection)
        super.onStop()
    }
}

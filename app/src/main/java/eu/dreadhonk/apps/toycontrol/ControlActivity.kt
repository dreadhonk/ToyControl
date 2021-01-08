package eu.dreadhonk.apps.toycontrol

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.*
import android.util.Log
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import eu.dreadhonk.apps.toycontrol.control.DeviceEvent
import eu.dreadhonk.apps.toycontrol.control.DeviceOutputEvent
import eu.dreadhonk.apps.toycontrol.control.SimpleControlMode
import eu.dreadhonk.apps.toycontrol.control.ToyControlService
import eu.dreadhonk.apps.toycontrol.data.DeviceWithIO
import eu.dreadhonk.apps.toycontrol.ui.IntensityControl
import eu.dreadhonk.apps.toycontrol.ui.LiveView
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

class ControlActivity : AppCompatActivity() {
    private val controls = ArrayList<IntensityControl>()
    private lateinit var targetLayout: LinearLayout
    private var mService: ToyControlService? = null

    private lateinit var liveView: LiveView

    private class DeviceEventHandler: Handler {
        private lateinit var ownerRef: WeakReference<ControlActivity>

        constructor(owner: ControlActivity): super() {
            ownerRef = WeakReference(owner)
        }

        override fun handleMessage(msg: Message) {
            Log.v("ControlActivity.DeviceEventHandler", "received message: "+msg.toString())

            val owner = ownerRef.get()
            if (owner == null) {
                throw RuntimeException("owner is gone")
            }

            val obj = msg.obj
            if (obj is DeviceOutputEvent) {
                owner.updateDeviceValues(obj.deviceId, obj.motors)
            } else if (obj is DeviceEvent) {
                /* when (obj.type) {
                    DeviceEventType.DEVICE_ONLINE ->
                        addDevice(obj.deviceId)
                    else ->
                        removeDevice(obj.deviceId)
                } */
            }
        }
    }

    private val mReceiver = DeviceEventHandler(this)

    private val mConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val remoteService = (service as ToyControlService.Binder).connect()
            mService = remoteService
            remoteService.registerDeviceEventListener(Messenger(mReceiver))
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
        liveView = findViewById<LiveView>(R.id.live_view)
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
                val mode = mService?.getSimpleControlMode(device.device.id, output)
                if (mode != null) {
                    newControl.mode = mode
                }
                controls.add(newControl)
                targetLayout.addView(
                    newControl,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1.0f
                    )
                )
                addOutputTrace(deviceOutputToTraceId(device.device.id, output))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, ToyControlService::class.java).also { intent ->
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()
        liveView.onResume()
    }

    override fun onPause() {
        liveView.onPause()
        super.onPause()
    }

    override fun onStop() {
        targetLayout.removeAllViews()
        controls.clear()
        unbindService(mConnection)
        super.onStop()
    }

    private fun deviceOutputToTraceId(deviceId: Long, output: Int): Long {
        return (deviceId shl 8) or (output and 0xff).toLong()
    }

    private fun addOutputTrace(traceId: Long) {
        Log.v("ControlActivity", "adding trace " + traceId)
        liveView.addTrace(traceId, Color.MAGENTA)
    }

    private fun removeOutputTrace(traceId: Long) {
        liveView.removeTrace(traceId)
    }

    private fun updateDeviceValues(deviceId: Long, values: FloatArray) {
        for (i in 0 until values.size) {
            val traceId = deviceOutputToTraceId(deviceId, i)
            Log.v("ControlActivity", "updating trace " + traceId)
            liveView.setTraceValue(traceId, values[i])
        }
    }
}

package eu.dreadhonk.apps.toycontrol

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import eu.dreadhonk.apps.toycontrol.control.MathUtil
import eu.dreadhonk.apps.toycontrol.ui.ValueView

class ControlActivity : AppCompatActivity(), SensorEventListener {

    private var xValueView: ValueView? = null
    private var yValueView: ValueView? = null
    private var zValueView: ValueView? = null
    private var playValueView: ValueView? = null

    private var accum: Float = 0.0f
    private val accumDecay: Float = 0.95f

    private lateinit var sensors: SensorManager
    private lateinit var plainAccel: Sensor
    private lateinit var gravity: Sensor
    private lateinit var linearAccel: Sensor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)

        xValueView = findViewById<ValueView>(R.id.vacc_x).also {
            it.showSubValueA = true
            it.showSubValueB = true
        }
        yValueView = findViewById<ValueView>(R.id.vacc_y).also {
            it.showSubValueA = true
            it.showSubValueB = true
        }
        zValueView = findViewById<ValueView>(R.id.vacc_z).also {
            it.showSubValueA = true
            it.showSubValueB = true
        }
        playValueView = findViewById<ValueView>(R.id.vplay).also {
            it.minimumValue = 0.0f
            it.maximumValue = 25.0f
        }

        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        plainAccel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        gravity = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)!!
        linearAccel = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)!!
    }

    override fun onStart() {
        super.onStart()
        Log.d("ControlActivity", "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d("ControlActivity", "onResume")
        sensors.registerListener(this, plainAccel, SensorManager.SENSOR_DELAY_UI)
        sensors.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI)
        sensors.registerListener(this, linearAccel, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onStop() {
        sensors.unregisterListener(this)
        super.onStop()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor) {
        plainAccel -> {
            val normVec = MathUtil.normalise(event.values)
            xValueView!!.currentValue = normVec[0]
            yValueView!!.currentValue = normVec[1]
            zValueView!!.currentValue = normVec[2]
        }
        linearAccel -> {
            val factor = 1.0f / 40.0f
            val length = MathUtil.length(event.values)
            val normVec = floatArrayOf(
                event.values[0] * factor,
                event.values[1] * factor,
                event.values[2] * factor
            )
            xValueView!!.subValueA = normVec[0]
            yValueView!!.subValueA = normVec[1]
            zValueView!!.subValueA = normVec[2]

            accum = Math.max(length, accum * accumDecay)
            playValueView!!.currentValue = accum
        }
        gravity -> {
            val normVec = MathUtil.normalise(event.values)
            xValueView!!.subValueB = normVec[0]
            yValueView!!.subValueB = normVec[1]
            zValueView!!.subValueB = normVec[2]
        }
        else -> null;
        }
    }
}

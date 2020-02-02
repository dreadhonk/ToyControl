package eu.dreadhonk.apps.toycontrol.control

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import org.metafetish.buttplug.core.Messages.SingleMotorVibrateCmd
import org.metafetish.buttplug.core.Messages.VibrateCmd
import java.util.concurrent.atomic.AtomicInteger

class ControlThread(service: ToyControlService,
                    interval: Long,
                    sensors: SensorManager) : Runnable, SensorEventListener {
    private val service = service;
    private val interval = interval;
    private val sensors = sensors

    private data class Vector(var x: Float = 0.0f, var y: Float = 0.0f, var z: Float = 0.0f) {
    }

    private var gravity = Vector()


    override fun run() {
        // TODO: this should arguably move somewhere else
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if (sensor == null) {
            Log.e("ControlThread", "no gravity sensor! this demo won't be fun")
        } else {
            sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }


        while (true) {
            Log.v("ControlThread", "loop")
            if (sensor != null) {
                do_the_thing(sensor)
            }
            Thread.sleep(interval);
        }
    }

    fun do_the_thing(sensor: Sensor) {
        Log.v("ControlThread", String.format("%.3f %.3f %.3f", gravity.x, gravity.y, gravity.z))
        val len = Math.sqrt((gravity.x * gravity.x + gravity.y * gravity.y + gravity.z * gravity.z).toDouble())
        if (len < 1.0) {
            return
        }

        val gravity_z = Math.max(gravity.z.toDouble() / len, 0.0)
        val client = service.client
        for (deviceIndex in client.devices.keys) {
            val device = client.devices.get(deviceIndex)!!
            Log.v("ControlThread", String.format("setting %.3f to %s", gravity_z, device.deviceName))
            client.sendDeviceMessage(deviceIndex, SingleMotorVibrateCmd(deviceIndex, gravity_z, client.nextMsgId))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        gravity.x = event!!.values[0]
        gravity.y = event!!.values[1]
        gravity.z = event!!.values[2]
    }
}
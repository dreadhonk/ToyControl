package eu.dreadhonk.apps.toycontrol.control

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.widget.Toast
import org.metafetish.buttplug.client.ButtplugClient
import org.metafetish.buttplug.core.Messages.SingleMotorVibrateCmd

class ToyController(private val sensors: SensorManager,
                    private val context: Context,
                    private val client: ButtplugClient): SensorEventListener {

    private var gravityNode = NormalisedGravityNode()
    private var quantiser = QuantizerNode(20)
    private var rateLimiter = RateLimitNode(250, 1)

    init {
        quantiser.deadZone = 0.1f
    }

    fun start() {
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if (sensor == null) {
            throw RuntimeException("failed to find gravity sensor!")
        }

        sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensors.unregisterListener(this)
    }

    private fun propagateUpdate() {
        quantiser.inputs[0] = gravityNode.outputs[2]
        if (!quantiser.update()) {
            // Log.v("ToyController", "update swallowed by quantiser")
            return;
        }

        rateLimiter.inputs[0] = quantiser.outputs[0]
        if (!rateLimiter.update()) {
            // TODO: if the rate-limiter swallows an update, we have to invalidate the node to
            // ensure that we get another chance at updating (if the rate limiter is behind a
            // quantiser)
            Log.v("ToyController", "update swallowed by rate limiter")
            return;
        }

        Log.i("ToyController", String.format("toy speed update to: %.2f", rateLimiter.outputs[0]))

        for (deviceIndex in client.devices.keys) {
            val device = client.devices.get(deviceIndex)!!
            Log.v("ControlThread", String.format("setting %.3f to %s", rateLimiter.outputs[0], device.deviceName))
            client.sendDeviceMessage(deviceIndex, SingleMotorVibrateCmd(deviceIndex, rateLimiter.outputs[0].toDouble(), client.nextMsgId))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent) {
        gravityNode.push(event.values)
        propagateUpdate()
    }
}
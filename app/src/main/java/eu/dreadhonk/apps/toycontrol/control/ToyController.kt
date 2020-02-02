package eu.dreadhonk.apps.toycontrol.control

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import org.metafetish.buttplug.client.ButtplugClient
import org.metafetish.buttplug.core.Messages.SingleMotorVibrateCmd
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ToyController(private val sensors: SensorManager,
                    private val context: Context,
                    private val client: ButtplugClient): SensorEventListener {

    private var gravityNode = NormalisedGravityNode()
    private var quantiser = QuantizerNode(20)
    private var rateLimiter = RateLimitNode(250, 1)

    companion object {
        public const val UPDATE_ON_INPUT_CHANGE = -1L;
        public const val UPDATE_IMMEDIATELY = 0L;
    }

    private class Worker(private val updateFun: ()->Long): Thread() {
        private val jobQueue = LinkedBlockingQueue<Runnable>();

        override fun run() {
            var nextEvent: Long = -1

            while (!isInterrupted()) {
                var timeout: Long = 1
                var timeoutUnit = TimeUnit.DAYS
                if (nextEvent >= 0) {
                    timeout = nextEvent - SystemClock.elapsedRealtime()
                    timeoutUnit = TimeUnit.MILLISECONDS
                    if (timeout < 0) {
                        timeout = 0
                    }
                }

                // waaaait for it: execute all jobs which need executing
                var job: Runnable? = jobQueue.poll(timeout, timeoutUnit)
                while (job != null) {
                    job.run()
                    job = jobQueue.poll()
                }

                // now update the graph
                val idleTime = updateFun()
                if (idleTime < 0) {
                    nextEvent = -1
                } else {
                    nextEvent = SystemClock.elapsedRealtime() + idleTime
                }
            }
        }

        fun post(job: Runnable) {
            jobQueue.put(job)
        }

        fun post(job: () -> Any) {
            jobQueue.put(Runnable { job() })
        }
    }

    private val worker = Worker { do_update() }

    init {
        quantiser.deadZone = 0.1f
    }

    fun start() {
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if (sensor == null) {
            throw RuntimeException("failed to find gravity sensor!")
        }

        worker.start()
        sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensors.unregisterListener(this)
        worker.interrupt()
    }

    private fun do_update(): Long {
        quantiser.inputs[0] = gravityNode.outputs[2]
        var delay = quantiser.update()
        if (delay != UPDATE_IMMEDIATELY && !rateLimiter.invalidated) {
            // Log.v("ToyController", "update swallowed by quantiser")
            return delay;
        }

        rateLimiter.inputs[0] = quantiser.outputs[0]
        delay = rateLimiter.update()
        if (delay != UPDATE_IMMEDIATELY) {
            Log.v("ToyController", String.format("update blocked by rate limiter for %d ms", delay))
            return delay;
        }

        Log.i("ToyController", String.format("toy speed update to: %.2f", rateLimiter.outputs[0]))

        for (deviceIndex in client.devices.keys) {
            val device = client.devices.get(deviceIndex)!!
            Log.v("ControlThread", String.format("setting %.3f to %s", rateLimiter.outputs[0], device.deviceName))
            client.sendDeviceMessage(deviceIndex, SingleMotorVibrateCmd(deviceIndex, rateLimiter.outputs[0].toDouble(), client.nextMsgId))
        }

        return UPDATE_ON_INPUT_CHANGE
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent) {
        worker.post {
            gravityNode.push(event.values)
        }
    }
}
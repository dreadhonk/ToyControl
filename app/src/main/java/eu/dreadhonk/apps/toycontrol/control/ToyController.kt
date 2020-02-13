package eu.dreadhonk.apps.toycontrol.control

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import eu.dreadhonk.apps.toycontrol.devices.DeviceManager
import org.metafetish.buttplug.core.Messages.SingleMotorVibrateCmd
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ToyController(private val sensors: SensorManager,
                    private val context: Context,
                    private val devices: DeviceManager): SensorEventListener {

    private var gravityNode = NormalisedGravityNode()
    private var quantiser = QuantizerNode(20)
    private var rateLimiter = RateLimitNode(250, 1)
    private var toy = ToyNode(1) { setToy(it) }

    companion object {
        public const val REQUIRES_INPUT_CHANGE = -1L;
        public const val RESULT_UPDATED = 0L;
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

        fun post(job: () -> Unit) {
            jobQueue.put(Runnable { job() })
        }
    }

    private val worker = Worker { do_update() }

    private val graph = ControlGraph()

    init {
        quantiser.deadZone = 0.1f

        graph.addNode(gravityNode)
        graph.addNode(quantiser)
        graph.addNode(rateLimiter)
        graph.addNode(toy)

        graph.link(gravityNode, 2, quantiser, 0)
        graph.link(quantiser, 0, rateLimiter, 0)
        graph.link(rateLimiter, 0, toy, 0)
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

    private fun setToy(values: FloatArray) {
        val value = values[0]

        Log.i("ToyController", String.format("toy speed update to: %.2f", value))
        for (provider in devices.providers) {
            for (device in provider.devices()) {
                for (motorIndex in device.motors.indices) {
                    Log.v("ToyController", "setting motor ${motorIndex} on device ${device.displayName} from provider ${provider.uri} to ${value}")
                    provider.setMotor(device.providerDeviceId, motorIndex, value)
                }
            }
        }
    }

    private fun do_update(): Long {
        return graph.update()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values
        worker.post {
            values.copyInto(gravityNode.inputs)
            gravityNode.invalidated = true
        }
    }
}
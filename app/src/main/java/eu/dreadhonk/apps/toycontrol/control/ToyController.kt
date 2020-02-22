package eu.dreadhonk.apps.toycontrol.control

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.*
import eu.dreadhonk.apps.toycontrol.data.Device
import eu.dreadhonk.apps.toycontrol.data.DeviceWithIO
import eu.dreadhonk.apps.toycontrol.devices.DeviceManager
import org.metafetish.buttplug.core.Messages.SingleMotorVibrateCmd
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ToyController(private val sensors: SensorManager,
                    private val context: Context,
                    private val devices: DeviceManager): SensorEventListener, LifecycleOwner {

    private val outputNodes = HashMap<Long, Node>();
    private val manualInputNodes = HashMap<Long, PassthroughNode>();

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

    private class SensorRegistration(
        private val listener: SensorEventListener,
        private val lifecycle: Lifecycle,
        private val sensor: Sensor,
        private val sensorManager: SensorManager,
        private val delay: Int): LifecycleObserver
    {
        private var _shouldBeEnabled: Boolean = true

        public var shouldBeEnabled: Boolean
            get() {
                return _shouldBeEnabled
            }
            set(v: Boolean) {
                if (v == _shouldBeEnabled) {
                    return
                }
                _shouldBeEnabled = v
                updateState()
            }

        private fun register() {
            sensorManager.registerListener(listener, sensor, delay)
        }

        private fun unregister() {
            sensorManager.unregisterListener(listener, sensor)
        }

        private fun updateState() {
            Log.v("SensorRegistration", "received state update")
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                Log.v("SensorRegistration", "not started -> not enabling")
                return
            }
            if (_shouldBeEnabled) {
                Log.v("SensorRegistration", "started -> enabling")
                register()
            } else {
                Log.v("SensorRegistration", "started -> disabling")
                unregister()
            }
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_START)
        fun onStart() {
            Log.v("SensorRegistration", "received start event")
            if (_shouldBeEnabled) {
                Log.v("SensorRegistration", "start event -> registering")
                register()
            }
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        fun onStop() {
            Log.v("SensorRegistration", "received stop event")
            if (_shouldBeEnabled) {
                Log.v("SensorRegistration", "stop event -> unregistering")
                unregister()
            }
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)

    private lateinit var sensorRegistration: SensorRegistration

    init {
    }

    fun start() {
        Log.v("ToyController", "start called")
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if (sensor == null) {
            throw RuntimeException("failed to find gravity sensor!")
        }

        sensorRegistration = SensorRegistration(
            this,
            lifecycleRegistry,
            sensor,
            sensors,
            SensorManager.SENSOR_DELAY_UI
        )
        sensorRegistration.shouldBeEnabled = false
        lifecycleRegistry.addObserver(sensorRegistration)

        lifecycleRegistry.setCurrentState(Lifecycle.State.STARTED)
        worker.start()
    }

    fun stop() {
        worker.interrupt()
        lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED)
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
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    fun enableSimpleControl() {
        // Default
    }

    fun setSimpleControlMode(device: Device, motor: Int, mode: SimpleControlMode) {

    }

    fun addDevice(device: DeviceWithIO, updateCallback: (FloatArray) -> Unit) {
        worker.post {
            val nIO = device.motors.size
            if (!outputNodes.containsKey(device.device.id)) {
                val toyNode = ToyNode(nIO) {
                    updateCallback(it)
                }
                val rateLimiter = RateLimitNode(100, nIO)
                val outputNode = PassthroughNode(nIO)

                graph.addNode(outputNode)
                graph.addNode(rateLimiter)
                graph.addNode(toyNode)

                Array<QuantizerNode>(nIO) { i ->
                    val motor = device.motors[i]
                    val q = QuantizerNode(motor.steps.toInt())
                    graph.addNode(q)
                    graph.link(outputNode, i, q, 0)
                    graph.link(q, 0, rateLimiter, i)
                    graph.link(rateLimiter, i, toyNode, i)
                    q
                }

                outputNodes[device.device.id] = outputNode
            }

            if (!manualInputNodes.containsKey(device.device.id)) {
                val node = PassthroughNode(nIO)
                manualInputNodes[device.device.id] = node
                graph.addNode(node)
            }

            val manualInput = manualInputNodes[device.device.id]!!
            val output = outputNodes[device.device.id]!!

            for (i in 0 until nIO) {
                graph.link(manualInput, i, output, i)
            }
        }
    }

    fun setManualInput(deviceId: Long, motor: Int, value: Float) {
        worker.post {
            val node = manualInputNodes[deviceId]!!
            node.inputs[motor] = value
            node.invalidated = true
        }
    }
}
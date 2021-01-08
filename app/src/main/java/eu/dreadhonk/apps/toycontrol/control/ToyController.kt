package eu.dreadhonk.apps.toycontrol.control

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import android.util.LongSparseArray
import androidx.lifecycle.*
import eu.dreadhonk.apps.toycontrol.data.DeviceWithIO
import eu.dreadhonk.apps.toycontrol.devices.DeviceManager
import java.util.*
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ToyController(private val sensors: SensorManager,
                    private val context: Context,
                    private val devices: DeviceManager): SensorEventListener, LifecycleOwner {

    private val supportNodes = HashMap<Long, ArrayList<Node>>();
    private val outputNodes = HashMap<Long, Node>();
    private val toyNodes = HashMap<Long, ToyNode>();
    private val manualInputNodes = HashMap<Long, PassthroughNode>();

    // TODO: do normalisation in separate node
    private val gravityNode = NormalisedGravityNode()
    private val linearAccelNode = PassthroughNode(3)
    private val shakeNode = ShakeIntensityNode()

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
    public val activeDevices: Int
        get() {
            return toyNodes.count()
        }

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

    private lateinit var gravityRegistration: SensorRegistration
    private lateinit var linearAccelRegistration: SensorRegistration

    init {
        graph.addNode(gravityNode)
        graph.addNode(linearAccelNode)
        graph.addNode(shakeNode)
        graph.link(linearAccelNode, 0, shakeNode, 0)
        graph.link(linearAccelNode, 1, shakeNode, 1)
        graph.link(linearAccelNode, 2, shakeNode, 2)
    }

    fun start() {
        Log.v("ToyController", "start called")
        val gravitySensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if (gravitySensor == null) {
            throw RuntimeException("failed to find gravity sensor!")
        }
        val linearAccelSensor = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (linearAccelSensor == null) {
            throw RuntimeException("failed to find linear acceleration sensor!")
        }

        gravityRegistration = SensorRegistration(
            this,
            lifecycleRegistry,
            gravitySensor,
            sensors,
            SensorManager.SENSOR_DELAY_UI
        )
        gravityRegistration.shouldBeEnabled = false
        lifecycleRegistry.addObserver(gravityRegistration)

        linearAccelRegistration = SensorRegistration(
            this,
            lifecycleRegistry,
            linearAccelSensor,
            sensors,
            SensorManager.SENSOR_DELAY_UI
        )
        linearAccelRegistration.shouldBeEnabled = false
        lifecycleRegistry.addObserver(linearAccelRegistration)

        lifecycleRegistry.setCurrentState(Lifecycle.State.STARTED)
        worker.start()
    }

    private fun updateSubscriptions() {
        val gravityUsed = graph.isNodeUsed(gravityNode)
        val linearAccelUsed = graph.isNodeUsed(linearAccelNode)
        Log.v("ToyController", "gravity needed = ${gravityUsed}, linear accel needed = ${linearAccelUsed}")
        gravityRegistration.shouldBeEnabled = gravityUsed
        linearAccelRegistration.shouldBeEnabled = linearAccelUsed
    }

    fun stop() {
        worker.interrupt()
        lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED)
    }

    private fun do_update(): Long {
        return graph.update()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values
        val sensorType = event.sensor.type
        worker.post {
            val node = when (sensorType) {
                Sensor.TYPE_LINEAR_ACCELERATION -> linearAccelNode
                Sensor.TYPE_GRAVITY -> gravityNode
                else -> null
            }
            if (node != null) {
                values.copyInto(node.inputs)
                node.invalidated = true
            }
        }
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    fun enableSimpleControl() {
        // Default
    }

    fun getSimpleControlMode(deviceId: Long, motor: Int): SimpleControlMode? {
        val fut = FutureTask<SimpleControlMode?>(fun(): SimpleControlMode? {
            val outputNode = outputNodes[deviceId]
            if (outputNode == null) {
                return null
            }
            val manualInputNode = manualInputNodes[deviceId]
            if (manualInputNode == null) {
                return null
            }
            if (graph.hasLink(manualInputNode, motor, outputNode, motor)) {
                return SimpleControlMode.MANUAL
            }
            if (graph.hasLink(gravityNode, 0, outputNode, motor)) {
                return SimpleControlMode.GRAVITY_X
            }
            if (graph.hasLink(gravityNode, 1, outputNode, motor)) {
                return SimpleControlMode.GRAVITY_Y
            }
            if (graph.hasLink(gravityNode, 2, outputNode, motor)) {
                return SimpleControlMode.GRAVITY_Z
            }
            if (graph.hasLink(shakeNode, 0, outputNode, motor)) {
                return SimpleControlMode.SHAKE
            }
            return null
        })
        worker.post(fut)
        return fut.get()
    }

    fun setSimpleControlMode(deviceId: Long, motor: Int, mode: SimpleControlMode) {
        worker.post {
            val outputNode = outputNodes[deviceId]
            if (outputNode != null) {
                when (mode) {
                    SimpleControlMode.MANUAL -> {
                        val manualInputNode = manualInputNodes[deviceId]!!
                        manualInputNode.invalidated = true
                        graph.link(manualInputNode, motor, outputNode, motor)
                    }
                    SimpleControlMode.GRAVITY_X -> {
                        gravityNode.invalidated = true
                        graph.link(gravityNode, 0, outputNode, motor)
                    }
                    SimpleControlMode.GRAVITY_Y -> {
                        gravityNode.invalidated = true
                        graph.link(gravityNode, 1, outputNode, motor)
                    }
                    SimpleControlMode.GRAVITY_Z -> {
                        gravityNode.invalidated = true
                        graph.link(gravityNode, 2, outputNode, motor)
                    }
                    SimpleControlMode.SHAKE -> {
                        shakeNode.invalidated = true
                        graph.link(shakeNode, 0, outputNode, motor)
                    }
                    else -> {
                        graph.unlinkInput(outputNode, motor)
                    }
                }
                updateSubscriptions()
            }
        }
    }

    fun addDevice(device: DeviceWithIO, updateCallback: (FloatArray) -> Unit) {
        val deviceId = device.device.id
        worker.post {
            val nIO = device.motors.size
            // need to keep track of the toy node separately in order to allow removal later;
            // the toy node has a side effect, so graph pruning will not remove it.
            var toyNode = toyNodes[deviceId];
            if (toyNode == null) {
                // TODO: also handle the case if hte IO count has changed for some reason
                toyNode = ToyNode(nIO) {
                    updateCallback(it)
                }
                toyNodes[deviceId] = toyNode
            }

            var outputNode = outputNodes[deviceId];
            if (outputNode == null) {
                outputNode = PassthroughNode(nIO)
                outputNodes[deviceId] = outputNode
            }

            var supportNodes = this.supportNodes[deviceId];
            if (supportNodes != null) {
                for (node in supportNodes) {
                    graph.removeNode(node)
                }
                supportNodes.clear()
            } else {
                supportNodes = ArrayList<Node>();
                this.supportNodes[deviceId] = supportNodes
            }

            val rateLimiter = RateLimitNode(100, nIO)
            supportNodes.add(rateLimiter)

            graph.addNode(outputNode)
            graph.addNode(rateLimiter)
            graph.addNode(toyNode)

            Array<QuantizerNode>(nIO) { i ->
                val motor = device.motors[i]
                val q = QuantizerNode(motor.steps.toInt())
                supportNodes.add(q)
                graph.addNode(q)
                graph.link(outputNode, i, q, 0)
                graph.link(q, 0, rateLimiter, i)
                graph.link(rateLimiter, i, toyNode, i)
                q
            }

            var manualInputNode = manualInputNodes[deviceId]
            if (manualInputNode == null) {
                manualInputNode = PassthroughNode(nIO)
                manualInputNodes[deviceId] = manualInputNode
                graph.addNode(manualInputNode)
            }

            for (i in 0 until nIO) {
                graph.link(manualInputNode, i, outputNode, i)
            }
            Log.i("ToyController", String.format("added device %d", deviceId))
            graph.dump("ToyController")
        }
    }

    fun removeDevice(deviceId: Long) {
        worker.post {
            val toyNode = toyNodes.remove(deviceId)
            if (toyNode != null) {
                graph.removeNode(toyNode)
            }
            val outputNode = outputNodes.remove(deviceId)
            if (outputNode != null) {
                graph.removeNode(outputNode)
            }
            val manualInput = manualInputNodes.remove(deviceId)
            if (manualInput != null) {
                graph.removeNode(manualInput)
            }
            val supportNodes = this.supportNodes.remove(deviceId)
            if (supportNodes != null) {
                for (node in supportNodes) {
                    graph.removeNode(node)
                }
            }
            Log.i("ToyController", String.format("removed device %d", deviceId))
            graph.dump("ToyController")
        }
    }

    fun post(job: () -> Unit) {
        worker.post(job)
    }

    fun setManualInput(deviceId: Long, motor: Int, value: Float) {
        worker.post {
            val node = manualInputNodes[deviceId]
            if (node != null) {
                node.inputs[motor] = value
                node.invalidated = true
            }
        }
    }

    fun getAllCurrentOutputs(): LongSparseArray<FloatArray> {
        val task = FutureTask<LongSparseArray<FloatArray>> {
            val result = LongSparseArray<FloatArray>()
            for (pair in toyNodes) {
                val values = FloatArray(pair.value.inputs.size)
                pair.value.inputs.copyInto(values)
                result.put(pair.key, values)
            }
            result
        }
        worker.post(task)
        return task.get()
    }
}
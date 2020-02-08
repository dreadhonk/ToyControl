package eu.dreadhonk.apps.toycontrol.control

import android.os.SystemClock

class RateLimitNode(
    public var minPeriod: Long = 250,
    nIO: Int = 1,
    public var gateFunction: (Float, Float) -> Float = {_, new -> new},
    init: Float = 0.0f,
    public var constantRate: Boolean = false): Node
{
    override var invalidated: Boolean = true
    override val inputs = FloatArray(nIO)
    private val buffer = FloatArray(nIO) { init }
    override val outputs = FloatArray(nIO)

    private var m_lastUpdate: Long = 0

    override fun update(): Long {
        val timestamp = SystemClock.elapsedRealtime()
        val tooEarlyBy = m_lastUpdate + minPeriod - timestamp

        for (i in inputs.indices) {
            buffer[i] = gateFunction(buffer[i], inputs[i])
        }

        if (tooEarlyBy > 0) {
            // ensure that this check re-runs on the next iteration
            invalidated = true
            return tooEarlyBy
        }

        m_lastUpdate = timestamp
        buffer.copyInto(outputs)
        inputs.copyInto(buffer)

        if (constantRate) {
            invalidated = true
            return minPeriod
        } else {
            invalidated = false
            return ToyController.UPDATE_IMMEDIATELY
        }
    }
}
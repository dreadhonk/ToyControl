package eu.dreadhonk.apps.toycontrol.control

import android.os.SystemClock

class RateLimitNode(
    public var minPeriod: Long = 250,
    nIO: Int = 1,
    public var gateFunction: (Float, Float) -> Float = {_, new -> new},
    init: Float = 0.0f,
    public var constantRate: Boolean = false,
    private val clock: () -> Long = SystemClock::elapsedRealtime): Node
{
    override var invalidated: Boolean = true
    override val inputs = FloatArray(nIO)
    private val buffer = FloatArray(nIO) { init }
    override val outputs = FloatArray(nIO)

    private var m_hadUpdate: Boolean = false
    private var m_lastUpdate: Long = 0

    override fun update(): Long {
        val timestamp = clock()
        val tooEarlyBy = m_lastUpdate + minPeriod - timestamp

        if (m_hadUpdate) {
            for (i in inputs.indices) {
                buffer[i] = gateFunction(buffer[i], inputs[i])
            }
        } else {
            inputs.copyInto(buffer)
            m_hadUpdate = true
        }

        if (tooEarlyBy > 0) {
            // ensure that this check re-runs on the next iteration
            invalidated = true
            return tooEarlyBy
        }

        m_lastUpdate = timestamp
        m_hadUpdate = false
        buffer.copyInto(outputs)
        inputs.copyInto(buffer)
        // request to be called again even if inputs have not changed
        // this is required to make it emit data even if nothing has
        // changed; this is required to be able to fire another output
        // even if no input change has happened
        invalidated = constantRate

        return ToyController.UPDATE_IMMEDIATELY
    }
}
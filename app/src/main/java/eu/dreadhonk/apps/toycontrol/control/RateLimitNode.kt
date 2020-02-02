package eu.dreadhonk.apps.toycontrol.control

import android.os.SystemClock

class RateLimitNode(minPeriod: Long = 250, nIO: Int = 1): Node {
    override var invalidated: Boolean = true
    override val inputs = FloatArray(nIO)
    override val outputs = FloatArray(nIO)

    private var m_lastUpdate: Long = 0
    public var minPeriod: Long = minPeriod

    override fun update(): Long {
        val timestamp = SystemClock.elapsedRealtime()
        val tooEarlyBy = m_lastUpdate + minPeriod - timestamp
        if (tooEarlyBy > 0) {
            // ensure that this check re-runs on the next iteration
            invalidated = true
            return tooEarlyBy
        }

        m_lastUpdate = timestamp
        inputs.copyInto(outputs)
        invalidated = false
        return ToyController.UPDATE_IMMEDIATELY
    }
}
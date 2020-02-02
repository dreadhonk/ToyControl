package eu.dreadhonk.apps.toycontrol.control

import android.os.SystemClock

class RateLimitNode(minPeriod: Long = 250, nIO: Int = 1): Node {
    override val inputs = FloatArray(nIO)
    override val outputs = FloatArray(nIO)

    private var m_lastUpdate: Long = 0
    public var minPeriod: Long = minPeriod

    override fun update(): Boolean {
        val timestamp = SystemClock.elapsedRealtime()
        if (timestamp - m_lastUpdate < minPeriod) {
            return false
        }
        m_lastUpdate = timestamp
        inputs.copyInto(outputs)
        return true
    }
}
package eu.dreadhonk.apps.toycontrol.control

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class QuantizerNode(stepCounta: Int = 2): Node {
    override var invalidated: Boolean = true
    override val inputs: FloatArray = floatArrayOf(0.0f)
    override val outputs: FloatArray = floatArrayOf(0.0f)

    private var m_stepCount: Int = 2
    private var m_deadZone: Float = 0.0f
    private var m_qinput: Int = -1

    public var stepCount: Int
        get() {
            return m_stepCount
        }
        set(value: Int) {
            if (value <= 1) {
                throw RuntimeException("invalid step count (must be greater than one): "+value.toString())
            }
            m_stepCount = value
        }

    public var deadZone: Float
        get() {
            return m_deadZone
        }
        set(value: Float) {
            if (value < 0.0f || value >= 0.5f) {
                throw RuntimeException("dead zone must be in interval [0..0.5)")
            }
            m_deadZone = value
        }

    init {
        stepCount = stepCounta
    }

    override fun update(): Long {
        invalidated = false
        val divider = (m_stepCount - 1).toFloat()
        val quantizedInput = Math.round(min(max(inputs[0], 0.0f), 1.0f) * divider)

        if (abs(quantizedInput - m_qinput) == 1 && m_qinput >= 0) {
            // consider dead zone. if the input is within m_deadZone of the step size around the
            // step position, we do not change the output
            val stepSize = 1.0f / divider;
            val sign = Math.signum((quantizedInput - m_qinput).toFloat())
            val allowedRange = stepSize / 2.0f - stepSize * m_deadZone
            val threshold = quantizedInput.toFloat() / divider - allowedRange * sign
            /* System.err.println(String.format(
                "input=%.5f  qinput=%d  old_qinput=%d  stepSize=%.3f  allowedRange=%.3f  threshold=%.3f",
                inputs[0],
                quantizedInput,
                m_qinput,
                stepSize,
                allowedRange,
                threshold
            )) */
            if (quantizedInput > m_qinput && inputs[0] < threshold) {
                return ToyController.UPDATE_ON_INPUT_CHANGE
            } else if (quantizedInput < m_qinput && inputs[0] > threshold) {
                return ToyController.UPDATE_ON_INPUT_CHANGE
            }
        }

        val changed = quantizedInput != m_qinput
        m_qinput = quantizedInput
        val quantizedOutput = quantizedInput.toFloat() / divider
        outputs[0] = quantizedOutput
        if (changed) {
            return ToyController.UPDATE_IMMEDIATELY
        } else {
            return ToyController.UPDATE_ON_INPUT_CHANGE
        }
    }
}
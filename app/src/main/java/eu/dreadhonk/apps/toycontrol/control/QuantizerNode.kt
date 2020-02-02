package eu.dreadhonk.apps.toycontrol.control

class QuantizerNode(stepCounta: Int = 2): Node {
    override val inputs: FloatArray = floatArrayOf(0.0f)
    override val outputs: FloatArray = floatArrayOf(0.0f)

    private var m_stepCount: Int = 2
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

    init {
        stepCount = stepCounta
    }

    override fun update(): Boolean {
        val divider = (m_stepCount - 1).toFloat()
        val quantizedInput = Math.round(inputs[0] * divider)
        val changed = quantizedInput != m_qinput
        m_qinput = quantizedInput
        val quantizedOutput = quantizedInput.toFloat() / divider
        outputs[0] = quantizedOutput
        return changed
    }
}
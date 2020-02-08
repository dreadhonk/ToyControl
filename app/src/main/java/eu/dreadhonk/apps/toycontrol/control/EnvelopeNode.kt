package eu.dreadhonk.apps.toycontrol.control

class EnvelopeNode(
    public var attack: Float = 1.0f,
    public var decay: Float = 0.95f,
    nIO: Int = 1,
    init: Float = 0.0f
): Node {
    override val inputs = FloatArray(nIO)
    private val buffer = FloatArray(nIO) { init }
    override val outputs = FloatArray(nIO)

    override var invalidated: Boolean = true

    override fun update(): Long {
        for (i in inputs.indices) {
            val curr = buffer[i]
            val new = inputs[i]
            if (new < curr) {
                buffer[i] = Math.max(curr * decay, new)
            } else {
                buffer[i] = curr + (new - curr) * attack
            }
        }

        invalidated = false
        return ToyController.UPDATE_ON_INPUT_CHANGE
    }
}
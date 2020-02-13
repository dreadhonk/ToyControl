package eu.dreadhonk.apps.toycontrol.control

class EnvelopeNode(
    public var attack: Float = 1.0f,
    public var decay: Float = 0.95f,
    nIO: Int = 1,
    init: Float = 0.0f
): Node {
    override val inputs = FloatArray(nIO)
    override val outputs = FloatArray(nIO) { init }
    override val hasSideEffects = false

    override var invalidated: Boolean = true

    override fun update(): Long {
        for (i in inputs.indices) {
            val curr = outputs[i]
            val new = inputs[i]
            if (new < curr) {
                outputs[i] = Math.max(curr * decay, new)
            } else {
                outputs[i] = curr + (new - curr) * attack
            }
        }
        invalidated = false
        return ToyController.UPDATE_IMMEDIATELY
    }
}
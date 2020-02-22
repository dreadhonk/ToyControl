package eu.dreadhonk.apps.toycontrol.control

class PassthroughNode(nIO: Int): Node {
    override val inputs = FloatArray(nIO)
    override val outputs = FloatArray(nIO)
    override var invalidated: Boolean = false
    override val hasSideEffects: Boolean = false

    override fun update(): Long {
        inputs.copyInto(outputs)
        invalidated = false
        return ToyController.RESULT_UPDATED
    }
}
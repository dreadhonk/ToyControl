package eu.dreadhonk.apps.toycontrol.control

class SinkNode(nInputs: Int): Node {
    override val inputs = FloatArray(nInputs)
    override val outputs = FloatArray(0)
    override var invalidated = false
    override val hasSideEffects = true

    public var updateCalled = false

    override fun update(): Long {
        updateCalled = true
        return ToyController.REQUIRES_INPUT_CHANGE
    }
}
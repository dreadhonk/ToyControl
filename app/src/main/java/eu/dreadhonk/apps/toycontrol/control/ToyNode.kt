package eu.dreadhonk.apps.toycontrol.control

class ToyNode(nInputs: Int, private val callback: (FloatArray) -> Unit): Node {
    override var invalidated: Boolean = true
    override val outputs = FloatArray(0)
    override val inputs = FloatArray(nInputs)

    override fun update(): Long {
        callback(inputs)
        return ToyController.UPDATE_ON_INPUT_CHANGE
    }
}
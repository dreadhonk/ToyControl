package eu.dreadhonk.apps.toycontrol.control

class MagnitudeNode(
    nIO: Int = 3
): Node {
    override val inputs = FloatArray(nIO)
    override val outputs = FloatArray(1)
    override var invalidated: Boolean = true

    override fun update(): Long {
        outputs[0] = MathUtil.length(inputs)
        invalidated = false
        return ToyController.UPDATE_IMMEDIATELY
    }
}
package eu.dreadhonk.apps.toycontrol.control

class NormalisedGravityNode(private val lengthCutoff: Float = 0.0001f): GravityNode() {
    override var invalidated: Boolean = false
    override val outputs: FloatArray = floatArrayOf(0.0f, 0.0f, 0.0f)

    override fun update(): Long {
        MathUtil.normalise(inputs, lengthCutoff).copyInto(outputs)
        for (i in outputs.indices) {
            outputs[i] = MathUtil.clampNorm(outputs[i])
        }
        invalidated = false
        return ToyController.RESULT_UPDATED
    }

}
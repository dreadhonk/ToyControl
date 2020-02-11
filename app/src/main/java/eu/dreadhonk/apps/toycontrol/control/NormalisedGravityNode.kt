package eu.dreadhonk.apps.toycontrol.control

import kotlin.math.max
import kotlin.math.min

class NormalisedGravityNode: GravityNode() {
    override var invalidated: Boolean = false
    override val outputs: FloatArray = floatArrayOf(0.0f, 0.0f, 0.0f)

    override fun update(): Long {
        MathUtil.normalise(inputs).copyInto(outputs)
        for (i in outputs.indices) {
            outputs[i] = MathUtil.clampNorm(outputs[i])
        }
        invalidated = false
        return ToyController.UPDATE_IMMEDIATELY
    }

}
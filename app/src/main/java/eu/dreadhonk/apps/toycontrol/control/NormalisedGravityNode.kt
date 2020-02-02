package eu.dreadhonk.apps.toycontrol.control

import kotlin.math.max
import kotlin.math.min

open class NormalisedGravityNode: GravityNode() {
    override val outputs: FloatArray = floatArrayOf(0.0f, 0.0f, 0.0f)

    override fun push(inputs: FloatArray): Boolean {
        super.push(inputs)
        MathUtil.normalise(inputs).copyInto(outputs)
        for (i in outputs.indices) {
            outputs[i] = MathUtil.clampNorm(outputs[i])
        }
        // FIXME: avoid updating on each sensor update ... those may be pretty frequent
        return true
    }

}
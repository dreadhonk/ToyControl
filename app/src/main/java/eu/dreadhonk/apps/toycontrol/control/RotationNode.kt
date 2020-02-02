package eu.dreadhonk.apps.toycontrol.control

import android.util.Log

class RotationNode: ExternalNode {
    override val inputCount: Int = 3
    override val outputs: FloatArray = floatArrayOf(0.0f, 0.0f, 0.0f)

    companion object {
        private const val scaling = Math.PI.toFloat()
    }

    override fun push(inputs: FloatArray): Boolean {
        if (!super.push(inputs)) {
            return false
        }

        MathUtil.normalise(inputs).copyInto(outputs)
        outputs[0] = Math.acos(outputs[0].toDouble()).toFloat() / scaling
        outputs[1] = Math.acos(outputs[1].toDouble()).toFloat() / scaling
        outputs[2] = Math.acos(outputs[2].toDouble()).toFloat() / scaling
        return true
    }
}
package eu.dreadhonk.apps.toycontrol.control

import android.util.Log

class RotationNode: Node {
    override var invalidated: Boolean = false
    override val inputs = FloatArray(3)
    override val outputs = floatArrayOf(0.0f, 0.0f, 0.0f)

    companion object {
        private const val scaling = Math.PI.toFloat()
    }

    override fun update(): Long {
        MathUtil.normalise(inputs).copyInto(outputs)
        outputs[0] = Math.acos(outputs[0].toDouble()).toFloat() / scaling
        outputs[1] = Math.acos(outputs[1].toDouble()).toFloat() / scaling
        outputs[2] = Math.acos(outputs[2].toDouble()).toFloat() / scaling
        invalidated = true
        return ToyController.UPDATE_IMMEDIATELY
    }
}
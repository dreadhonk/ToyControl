package eu.dreadhonk.apps.toycontrol.control

class ShakeIntensityNode: Node {
    override var invalidated: Boolean = false

    private val magnitude = MagnitudeNode(3)
    private val rate = RateLimitNode(
        minPeriod=50,
        init=0.0f,
        // we want to take the maximum magnitude for shake purposes
        gateFunction={ old, new -> Math.max(old, new) },
        nIO=3,
        constantRate=true
    )

    override val inputs = magnitude.inputs
    override val outputs = FloatArray(1)
    override val hasSideEffects = false

    override fun update(): Long {
        // TODO: this needs scaling; when used with the TYPE_LINEAR_ACCELERATION sensor, this will
        // generally output values between 0 and 25 or so, which is out of range for the normal
        // flow.
        inputs.copyInto(magnitude.inputs)
        var result = magnitude.update()
        if (result == ToyController.REQUIRES_INPUT_CHANGE) {
            return result
        }

        magnitude.outputs.copyInto(rate.inputs)
        result = rate.update()
        if (result == ToyController.REQUIRES_INPUT_CHANGE) {
            return result
        }
        outputs[0] = rate.outputs[0] / 25.0f
        return result
    }
}
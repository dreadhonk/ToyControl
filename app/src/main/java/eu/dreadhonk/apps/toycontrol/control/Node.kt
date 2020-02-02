package eu.dreadhonk.apps.toycontrol.control

interface BaseNode {
    /**
     * @brief Output register for the outputs
     */
    val outputs: FloatArray

    var invalidated: Boolean
}

interface Node: BaseNode {
    /**
     * @brief Input register for the inputs
     */
    val inputs: FloatArray

    /**
     * @brief Update the nodes state.
     *
     * Return true if at least one output changed.
     */
    fun update(): Long
}

interface ExternalNode: BaseNode {
    val inputCount: Int

    fun push(inputs: FloatArray): Long {
        if (inputs.size != inputCount) {
            throw IllegalArgumentException(String.format("must have exactly %d inputs (got %d)", inputCount, inputs.size))
        }
        return ToyController.UPDATE_IMMEDIATELY
    }
}
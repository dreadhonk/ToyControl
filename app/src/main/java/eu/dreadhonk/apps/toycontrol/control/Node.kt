package eu.dreadhonk.apps.toycontrol.control

interface Node {
    /**
     * @brief Input register for the inputs
     */
    val inputs: FloatArray

    /**
     * @brief Output register for the outputs
     */
    val outputs: FloatArray


    var invalidated: Boolean

    val hasSideEffects: Boolean

    /**
     * @brief Update the nodes state.
     *
     * Return true if at least one output changed.
     */
    fun update(): Long
}
package eu.dreadhonk.apps.toycontrol.control

import kotlin.reflect.KClass

interface Node {
    /**
     * @brief Input register for the inputs
     */
    val inputs: FloatArray

    /**
     * @brief Output register for the outputs
     */
    val outputs: FloatArray

    /**
     * @brief Update the nodes state.
     *
     * Return true if at least one output changed.
     */
    fun update(): Boolean
}
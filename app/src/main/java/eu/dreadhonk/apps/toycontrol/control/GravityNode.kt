package eu.dreadhonk.apps.toycontrol.control

abstract class GravityNode: Node {
    override val inputs = FloatArray(3)
    override val hasSideEffects = false
}
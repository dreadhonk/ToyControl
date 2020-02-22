package eu.dreadhonk.apps.toycontrol.control

enum class SimpleControlMode(public val id: Int) {
    MANUAL(0),
    GRAVITY_X(1),
    GRAVITY_Y(2),
    GRAVITY_Z(3),
    SHAKE(4)
}
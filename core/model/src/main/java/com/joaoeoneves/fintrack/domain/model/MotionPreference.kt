package com.joaoeoneves.fintrack.domain.model

/** Whether decorative animations are reduced. [SYSTEM] follows the device's animator setting. */
enum class MotionPreference {
    SYSTEM,
    ON,
    OFF,
    ;

    /** True when animations should be reduced, given whether the system already asks for it. */
    fun resolve(systemReduced: Boolean): Boolean =
        when (this) {
            SYSTEM -> systemReduced
            ON -> true
            OFF -> false
        }
}

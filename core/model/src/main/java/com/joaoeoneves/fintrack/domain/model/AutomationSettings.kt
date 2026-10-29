package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * The automation state. [lineRecordedThrough] holds, per planned income line with automatic recording on, the last
 * month whose income was recorded (or deliberately left alone when it was switched on). The legacy fields are the
 * old single switch of the payday automation, kept only as migration input (see [incomeMigrationOf]).
 */
data class AutomationSettings(
    val legacyPaydayEnabled: Boolean = false,
    val legacyPaydayRecordedThrough: YearMonth? = null,
    val lineRecordedThrough: Map<String, YearMonth> = emptyMap(),
    /** Whether the salary pays the next month's plan; `null` until chosen (then follows the salary profile). */
    val salaryPaysNextMonth: Boolean? = null,
)

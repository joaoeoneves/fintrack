package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * The automation state. [lineRecordedThrough] holds, per planned income line with automatic recording on, the last
 * month whose income was recorded (or deliberately left alone when it was switched on).
 */
data class AutomationSettings(
    val lineRecordedThrough: Map<String, YearMonth> = emptyMap(),
    /** Whether the salary pays the next month's plan; `null` until chosen (then follows the salary profile). */
    val salaryPaysNextMonth: Boolean? = null,
)

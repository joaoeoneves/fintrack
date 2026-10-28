package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * The user's automation switches. [paydayRecordedThrough] is the cursor of the payday automation: the last month
 * whose income was recorded (or deliberately left alone when the switch was turned on); `null` while never enabled.
 */
data class AutomationSettings(
    val paydayEnabled: Boolean = false,
    val paydayRecordedThrough: YearMonth? = null,
)

package com.joaoeoneves.fintrack.ui.common.period

/** Previous/next callbacks of a [PeriodHeader]; either is `null` when there is no arrow (non-month periods). */
class PeriodNavigation(
    val onPrevious: (() -> Unit)?,
    val onNext: (() -> Unit)?,
    val canGoPrevious: Boolean = true,
    val canGoNext: Boolean = true,
    val isYear: Boolean = false,
)

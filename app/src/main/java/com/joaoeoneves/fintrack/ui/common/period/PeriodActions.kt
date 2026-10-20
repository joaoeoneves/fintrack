package com.joaoeoneves.fintrack.ui.common.period

import com.joaoeoneves.fintrack.domain.model.Period

/** Callbacks of [PeriodSelector], bundled so screens can pass them through a single parameter. */
data class PeriodActions(
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSelect: (Period) -> Unit,
)

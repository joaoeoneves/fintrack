package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.Period

/** Where the Plan tab can navigate to, bundled for detekt's LongParameterList. */
class PlanNavigation(
    val onOpenIncome: (Period) -> Unit,
    val onOpenEnvelopes: () -> Unit,
    val onOpenEnvelope: (String) -> Unit,
    val onOpenRecurring: () -> Unit,
    val onOpenCategories: () -> Unit,
    val onOpenSavings: () -> Unit,
)

/** Where the "Set up" rows lead, bundled for detekt's LongParameterList. */
class ConfigureActions(
    val onOpenIncome: () -> Unit,
    val onOpenEnvelopes: () -> Unit,
    val onOpenRecurring: () -> Unit,
    val onOpenCategories: () -> Unit,
)

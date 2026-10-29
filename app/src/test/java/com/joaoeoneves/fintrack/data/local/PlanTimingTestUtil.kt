package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Envelope

/** Budgets without the (wall-clock dependent) start month and versions, for content assertions. */
internal fun List<Budget>.untimed(): List<Budget> = map { it.copy(startMonth = null, versions = emptyList()) }

internal fun List<Envelope>.untimedEnvelopes() = map { it.copy(startMonth = null, versions = emptyList()) }

internal fun List<LocalBudget>.untimedLocal() = map { it.copy(startMonth = null, versions = emptyList()) }

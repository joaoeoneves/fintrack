package com.joaoeoneves.fintrack.domain.usecase

/**
 * What an automatic step did: how many records it created, and whether a failed read or write cut it short (so
 * the caller retries later). Nothing due is `AutomationOutcome(0)`; an unreachable server is `failed = true`.
 */
data class AutomationOutcome(
    val count: Int,
    val failed: Boolean = false,
)

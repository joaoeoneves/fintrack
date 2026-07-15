package ptech.joaoe.agenticusage.domain.model

import java.time.Instant

data class Income(
    val id: String,
    val source: String,
    val amountCents: Long,
    val date: Instant,
    val note: String?,
)

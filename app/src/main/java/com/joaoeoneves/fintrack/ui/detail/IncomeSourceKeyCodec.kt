package com.joaoeoneves.fintrack.ui.detail

import com.joaoeoneves.fintrack.domain.model.IncomeSourceKey
import com.joaoeoneves.fintrack.domain.model.SalaryComponent

private const val ROW_PREFIX = "row:"
private const val NAMED_PREFIX = "named:"
private const val ROW_PARTS = 3

/**
 * The inverse of `IncomeSourceKey.encode()` (`row:<component>|<extraId>|<lineId>` or `named:<normalised>`); `null`
 * for anything malformed, so a stale or hand-made route argument never crashes the detail page.
 */
fun decodeIncomeSourceKey(token: String): IncomeSourceKey? =
    when {
        token.startsWith(ROW_PREFIX) -> decodeRow(token.removePrefix(ROW_PREFIX))
        token.startsWith(NAMED_PREFIX) -> IncomeSourceKey.Named(token.removePrefix(NAMED_PREFIX))
        else -> null
    }

private fun decodeRow(body: String): IncomeSourceKey? {
    val parts = body.split('|', limit = ROW_PARTS)
    if (parts.size != ROW_PARTS) return null
    val component =
        parts[0].takeIf { it.isNotEmpty() }?.let { name -> SalaryComponent.entries.firstOrNull { it.name == name } }
    return if (parts[0].isNotEmpty() && component == null) {
        null
    } else {
        IncomeSourceKey.Row(component, parts[1].takeIf { it.isNotEmpty() }, parts[2].takeIf { it.isNotEmpty() })
    }
}

package com.joaoeoneves.fintrack.ui.plan.salary

import com.joaoeoneves.fintrack.domain.model.MAX_EXTRA_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.SalaryExtra
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountScope
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryExtraDeductions
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.codePointLength

/**
 * The extra this sheet form describes under [id] and the fields that are missing or malformed (then the extra is
 * `null`). NET mode only takes a plain yearly amount: no deductions, no fee.
 */
internal fun SalaryExtraForm.toExtra(
    id: String,
    mode: SalaryInputMode,
): Pair<SalaryExtra?, Set<SalaryExtraField>> {
    val gross = mode == SalaryInputMode.GROSS
    val type = if (gross) amountType else SalaryExtraAmountType.FIXED_CENTS
    val trimmedName = name.trim()
    val amount = parseExtraAmount(type, amountText, amountCents)
    val fee = if (gross) parsePercentPpm(feeText).orZeroIfEmpty() else 0
    val invalid =
        buildSet {
            if (trimmedName.codePointLength() !in 1..MAX_EXTRA_NAME_LENGTH) add(SalaryExtraField.NAME)
            if (amount == null) add(SalaryExtraField.AMOUNT)
            if (fee == null) add(SalaryExtraField.FEE)
        }
    val complete = invalid.isEmpty() && months.isNotEmpty()
    val extra =
        if (complete && amount != null && fee != null) {
            SalaryExtra(
                id = id,
                name = trimmedName,
                amountType = type,
                amount = amount,
                months = months.toSet(),
                deductions = if (gross) deductions else SalaryExtraDeductions.NONE,
                feePpm = fee,
                amountScope = if (months.size > 1) amountScope else SalaryExtraAmountScope.TOTAL,
            )
        } else {
            null
        }
    return extra to invalid
}

private fun parseExtraAmount(
    type: SalaryExtraAmountType,
    text: String,
    cents: Long,
): Long? =
    when (type) {
        SalaryExtraAmountType.FIXED_CENTS -> cents.takeIf { it > 0L }
        SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM -> (parseMultiplierPpm(text) as? Parsed.Value)?.value?.toLong()
        // A zero share pays nothing, so it counts as missing like the other types' blank / zero.
        SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM ->
            (parsePercentPpm(text) as? Parsed.Value)?.value?.takeIf { it > 0 }?.toLong()
    }

/** A blank value is 0, a malformed one `null`. */
private fun Parsed<Int>.orZeroIfEmpty(): Int? =
    when (this) {
        is Parsed.Value -> value
        Parsed.Empty -> 0
        Parsed.Invalid -> null
    }

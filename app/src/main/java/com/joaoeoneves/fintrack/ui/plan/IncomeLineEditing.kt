package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.MAX_PLAN_LABEL_LENGTH
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.ui.common.UiText

/** Why [label] can't be saved as a planned-income label, or `null` when it can. Judged after trimming. */
fun validateIncomeLineLabel(label: String): UiText? {
    val trimmed = label.trim()
    return when {
        trimmed.isEmpty() -> UiText.StringResource(R.string.error_plan_label_required)
        trimmed.codePointLength() > MAX_PLAN_LABEL_LENGTH -> UiText.StringResource(R.string.error_plan_label_too_long)
        else -> null
    }
}

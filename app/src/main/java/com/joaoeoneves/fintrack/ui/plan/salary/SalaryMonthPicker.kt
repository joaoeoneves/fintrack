package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.runtime.Composable
import com.joaoeoneves.fintrack.ui.plan.MonthChips

/**
 * The selection after tapping [month]: a selected month is removed (the last one stays), a new one is added and,
 * with a [max], the oldest selections are dropped to stay within it.
 */
internal fun toggleMonth(
    selected: List<Int>,
    month: Int,
    max: Int?,
): List<Int> =
    when {
        month in selected -> if (selected.size > 1) selected - month else selected
        max != null -> (selected + month).takeLast(max)
        else -> selected + month
    }

/** The month grid of the salary form: [max] months at most (`null` = any number), at least one. */
@Composable
internal fun SalaryMonthPicker(
    title: String,
    selected: List<Int>,
    onChange: (List<Int>) -> Unit,
    max: Int? = null,
) {
    MonthChips(title = title, selected = selected.toSet(), onToggle = { onChange(toggleMonth(selected, it, max)) })
}

/** A [SalaryMonthPicker] for a single month. */
@Composable
internal fun SalaryMonthPicker(
    title: String,
    selected: Int,
    onChange: (Int) -> Unit,
) {
    SalaryMonthPicker(title, listOf(selected), { onChange(it.last()) }, max = 1)
}

package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.designsystem.PendingLabels

/** The [PendingLabels] of a pending row; [income] words the check as "receive" rather than "confirm". */
@Composable
fun rememberPendingLabels(income: Boolean = false): PendingLabels =
    PendingLabels(
        confirm = stringResource(if (income) R.string.income_mark_received else R.string.recurring_confirm),
        more = stringResource(R.string.pending_more_options),
        skip = stringResource(R.string.recurring_skip),
        changeAmount = stringResource(R.string.pending_change_amount),
    )

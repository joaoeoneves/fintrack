package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.IncomeRow
import com.joaoeoneves.fintrack.ui.common.formatAmountCents

/** What an income row shows as its amount: what was received when it was, else the planned amount. */
@Composable
internal fun rowValueText(
    row: IncomeRow,
    receipts: IncomeReceipts,
): String =
    if (row.cents > 0L) {
        formatAmountCents(receipts.receivedCents[row.key] ?: row.cents)
    } else {
        stringResource(R.string.plan_not_this_month)
    }

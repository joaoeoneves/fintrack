package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow

/**
 * The rows of the "Set up" screen: income, envelopes, bills and categories, each with a one-line status.
 * [extraRows] is the place for further global set-up rows (each preceded by its own [Hairline]).
 */
@Composable
fun ConfigureSection(
    status: ConfigureStatus,
    actions: ConfigureActions,
    modifier: Modifier = Modifier,
    extraRows: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsRow(
            title = stringResource(R.string.plan_configure_income),
            supportingText = incomeStatus(status),
            onClick = actions.onOpenIncome,
            showChevron = true,
        )
        Hairline()
        SettingsRow(
            title = stringResource(R.string.plan_configure_envelopes),
            supportingText =
                if (status.envelopeCount > 0) {
                    pluralStringResource(R.plurals.envelopes_status, status.envelopeCount, status.envelopeCount)
                } else {
                    stringResource(R.string.plan_configure_none_yet)
                },
            onClick = actions.onOpenEnvelopes,
            showChevron = true,
        )
        Hairline()
        SettingsRow(
            title = stringResource(R.string.plan_configure_bills),
            supportingText =
                if (status.billCount > 0) {
                    stringResource(
                        R.string.plan_configure_bills_status,
                        status.billCount,
                        formatAmountCents(status.billsMonthlyCents),
                    )
                } else {
                    stringResource(R.string.plan_configure_none_yet)
                },
            onClick = actions.onOpenRecurring,
            showChevron = true,
        )
        Hairline()
        SettingsRow(
            title = stringResource(R.string.plan_configure_categories),
            supportingText =
                pluralStringResource(
                    R.plurals.plan_configure_categories_status,
                    status.categoryCount,
                    status.categoryCount,
                ),
            onClick = actions.onOpenCategories,
            showChevron = true,
        )
        extraRows()
    }
}

@Composable
private fun incomeStatus(status: ConfigureStatus): String =
    if (status.incomeLineCount > 0) {
        stringResource(
            R.string.plan_configure_income_status,
            formatAmountCents(status.incomeMonthlyCents),
            status.incomeLineCount,
        )
    } else {
        stringResource(R.string.plan_configure_not_set)
    }

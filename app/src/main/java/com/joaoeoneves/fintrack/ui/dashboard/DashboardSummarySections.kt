package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.activity.analytics.ChangeLine
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.AnimatedAmountText
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.theme.AmountLarge
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MIN_AMOUNT_SP = 20
private const val MAX_AMOUNT_SP = 32

/** Test tags of Home's summary blocks. */
object DashboardTags {
    const val INCOME_CARD = "dashboard_income_card"
    const val SPENDING_CARD = "dashboard_spending_card"
    const val INVESTED_ROW = "dashboard_invested_row"
    const val LEFTOVER_ROW = "dashboard_leftover_row"
}

/** The "Income" card: what was received in the period by date, and what the income lines expected. */
@Composable
fun IncomeSummaryCard(
    state: DashboardUiState.Content,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SummaryCard(
        title = stringResource(R.string.home_income_title),
        cents = state.incomeCents,
        onClick = onClick,
        modifier = modifier.testTag(DashboardTags.INCOME_CARD),
    ) {
        state.expectedIncomeCents?.let {
            Text(
                text = stringResource(R.string.home_income_of_expected, formatAmountCents(it)),
                style = MaterialTheme.typography.bodyMedium,
                color = FinTrackTheme.colors.textSecondary,
            )
        }
    }
}

/** The "Spending" card: spending-kind outflow of the period and, for a month, the change against the average. */
@Composable
fun SpendingSummaryCard(
    state: DashboardUiState.Content,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SummaryCard(
        title = stringResource(R.string.home_spending_title),
        cents = state.spendingCents,
        onClick = onClick,
        modifier = modifier.testTag(DashboardTags.SPENDING_CARD),
    ) {
        state.spendingVsAveragePercent?.let { percent ->
            ChangeLine(stringResource(R.string.activity_vs_average, state.spendingAverageMonths), percent)
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    cents: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        SectionTitle(title = title)
        AnimatedAmountText(
            cents = cents,
            style = AmountLarge,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_AMOUNT_SP.sp, maxFontSize = MAX_AMOUNT_SP.sp),
        )
        Column(modifier = Modifier.padding(top = Dimens.Space4)) { detail() }
    }
}

/** "Invested: X this month · Y in <year> ›" (or the year only when a whole year is shown). */
@Composable
fun InvestedRow(
    figure: InvestedFigure,
    month: YearMonth,
    isCurrentMonth: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val year = figure.year.toString()
    val yearText = formatAmountCents(figure.yearCents)
    val text =
        when {
            figure.monthCents == null -> stringResource(R.string.home_invested_year, year, yearText)
            isCurrentMonth ->
                stringResource(R.string.home_invested_month, formatAmountCents(figure.monthCents), yearText, year)
            else ->
                stringResource(
                    R.string.home_invested_other_month,
                    formatAmountCents(figure.monthCents),
                    remember(month, locale) { monthName(month, locale) },
                    yearText,
                    year,
                )
        }
    Column(modifier = modifier.fillMaxWidth().testTag(DashboardTags.INVESTED_ROW)) {
        Hairline()
        SettingsRow(title = text, onClick = onClick, showChevron = true)
        Hairline()
    }
}

/** "Left over until <month>: average X per month". */
@Composable
fun LeftoverRow(
    figure: LeftoverFigure,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val month = remember(figure.upTo, locale) { monthName(figure.upTo, locale) }
    Text(
        text = stringResource(R.string.home_leftover, month, formatAmountCents(figure.averageCents)),
        style = MaterialTheme.typography.bodyMedium,
        color = FinTrackTheme.colors.textSecondary,
        modifier = modifier.fillMaxWidth().testTag(DashboardTags.LEFTOVER_ROW),
    )
}

private fun monthName(
    month: YearMonth,
    locale: Locale,
): String = DateTimeFormatter.ofPattern("LLLL", locale).format(month)

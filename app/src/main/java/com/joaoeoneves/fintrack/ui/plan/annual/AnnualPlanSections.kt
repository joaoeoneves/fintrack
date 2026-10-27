package com.joaoeoneves.fintrack.ui.plan.annual

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.AnnualGroup
import com.joaoeoneves.fintrack.domain.model.AnnualIncome
import com.joaoeoneves.fintrack.domain.model.AnnualPot
import com.joaoeoneves.fintrack.domain.model.AnnualSubscription
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.period.formatShortMonthName
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.ThinProgressBar
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth
import kotlin.math.abs

@Composable
private fun LabelAmountRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = Amount, color = valueColor)
    }
}

/** Regular income, the extras (tap to expand the list with each one's month) and the total. */
@Composable
fun AnnualIncomeCard(
    income: AnnualIncome,
    year: Int,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(label = stringResource(R.string.annual_income_title), modifier = modifier) {
        LabelAmountRow(stringResource(R.string.annual_income_regular), formatAmountCents(income.regularCents))
        if (income.extras.isNotEmpty()) {
            Hairline()
            LabelAmountRow(
                label = stringResource(R.string.annual_income_extras),
                value = formatAmountCents(income.extrasCents),
                modifier = Modifier.clickable(role = Role.Button) { expanded = !expanded },
            )
            if (expanded) {
                val locale = currentLocale()
                income.extras.forEach { extra ->
                    val month = formatShortMonthName(YearMonth.of(year, extra.month), locale)
                    SubRow(
                        label = stringResource(R.string.annual_extra_in_month, extra.line.label, month),
                        value = formatAmountCents(extra.cents),
                    )
                }
            }
        }
        Hairline()
        LabelAmountRow(stringResource(R.string.annual_income_total), formatAmountCents(income.totalCents))
    }
}

@Composable
private fun SubRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = FinTrackTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = Amount, color = FinTrackTheme.colors.textSecondary)
    }
}

/** Planned vs year-to-date actual per group, with a thin bar; groups with nothing at all are left out. */
@Composable
fun AnnualGroupsCard(
    groups: List<AnnualGroup>,
    modifier: Modifier = Modifier,
) {
    val shown = groups.filter { it.plannedCents > 0 || it.actualCents > 0 }
    if (shown.isEmpty()) return
    SettingsGroup(label = stringResource(R.string.plan_groups_title), modifier = modifier) {
        shown.forEach { group ->
            AnnualGroupRow(group)
            Hairline()
        }
    }
}

@Composable
private fun AnnualGroupRow(group: AnnualGroup) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space12)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.group?.name ?: stringResource(R.string.plan_group_other),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    stringResource(
                        R.string.annual_actual_of_planned,
                        formatAmountCents(group.actualCents),
                        formatAmountCents(group.plannedCents),
                    ),
                style = Amount,
                color = if (group.isOver) FinTrackTheme.colors.negative else MaterialTheme.colorScheme.onSurface,
            )
        }
        ThinProgressBar(
            progress = group.fraction(),
            isOver = group.isOver,
            modifier = Modifier.padding(top = Dimens.Space8),
        )
    }
}

private fun AnnualGroup.fraction(): Float {
    if (plannedCents <= 0L) return 0f
    return (actualCents.toDouble() / plannedCents.toDouble()).toFloat().coerceIn(0f, 1f)
}

/** One line per earmark pot: what is left of its year income after its categories' planned amounts. */
@Composable
fun AnnualPotsCard(
    pots: List<AnnualPot>,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.annual_pots_title), modifier = modifier) {
        pots.forEachIndexed { index, pot ->
            if (index > 0) Hairline()
            AnnualPotRow(pot)
        }
    }
}

@Composable
private fun AnnualPotRow(pot: AnnualPot) {
    val name = pot.group?.name ?: pot.category?.displayName ?: stringResource(R.string.plan_pot)
    val left = pot.leftoverCents
    val text =
        stringResource(
            if (left >= 0) R.string.plan_pot_left else R.string.plan_pot_over,
            name,
            formatAmountCents(abs(left)),
            formatAmountCents(pot.incomeCents),
        )
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (left >= 0) FinTrackTheme.colors.textSecondary else FinTrackTheme.colors.negative,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.SettingsRowMinHeight)
                .padding(vertical = Dimens.Space8),
    )
}

/** The yearly recurring expenses with the month each is due and its amount. */
@Composable
fun AnnualSubscriptionsCard(
    subscriptions: List<AnnualSubscription>,
    year: Int,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    SettingsGroup(label = stringResource(R.string.annual_subscriptions_title), modifier = modifier) {
        subscriptions.forEachIndexed { index, subscription ->
            if (index > 0) Hairline()
            val month = formatShortMonthName(YearMonth.of(year, subscription.month), locale)
            LabelAmountRow(
                label = stringResource(R.string.annual_subscription_row, subscription.item.name, month),
                value = formatAmountCents(subscription.item.amountCents),
            )
        }
    }
}

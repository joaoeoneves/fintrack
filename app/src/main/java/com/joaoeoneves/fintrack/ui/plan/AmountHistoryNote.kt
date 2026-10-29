package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.AmountVersion
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.period.MonthPickerDialog
import com.joaoeoneves.fintrack.ui.common.period.formatMonthLabel
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth

/** What an item's previous amount was: [amountCents] applied until the end of month [until]. */
data class AmountHistoryNote(
    val amountCents: Long,
    val until: YearMonth,
)

/**
 * The one-line history of an item with [versions]: the amount before the latest version and the last month it
 * applied in, or null when there is no earlier amount to show.
 */
fun amountHistoryNote(versions: List<AmountVersion>): AmountHistoryNote? {
    val lastTwo = versions.sortedBy { it.from }.takeLast(2)
    val previous = lastTwo.firstOrNull()?.amountCents.takeIf { lastTwo.size == 2 }
    return previous?.let { AmountHistoryNote(it, lastTwo.last().from.minusMonths(1)) }
}

/** The grey "300 EUR until September 2026" line under an amount. */
@Composable
fun AmountHistoryText(
    note: AmountHistoryNote,
    modifier: Modifier = Modifier,
) {
    Text(
        text =
            stringResource(
                R.string.effective_from_history,
                formatAmountCents(note.amountCents),
                formatMonthLabel(note.until, currentLocale(), titlecase = false),
            ),
        style = MaterialTheme.typography.bodySmall,
        color = FinTrackTheme.colors.textSecondary,
        modifier = modifier,
    )
}

/** The "Valid from" row: the chosen plan [month], tapped to pick another (past months and the next one allowed). */
@Composable
fun EffectiveFromRow(
    month: YearMonth,
    onChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        title = stringResource(R.string.effective_from_title),
        value = formatMonthLabel(month, currentLocale()),
        onClick = { picking = true },
        showChevron = true,
        modifier = modifier,
    )
    if (picking) {
        MonthPickerDialog(
            initialMonth = month,
            currentMonth = YearMonth.now().plusMonths(1),
            onMonthSelected = {
                onChange(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

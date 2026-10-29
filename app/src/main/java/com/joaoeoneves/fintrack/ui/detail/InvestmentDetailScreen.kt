package com.joaoeoneves.fintrack.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.InstrumentAmount
import com.joaoeoneves.fintrack.ui.common.ExpenseRow
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.period.formatShortMonthName
import com.joaoeoneves.fintrack.ui.designsystem.DetailScaffold
import com.joaoeoneves.fintrack.ui.designsystem.EmptyState
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.MonthlyBar
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.theme.Dimens

private const val CONTEXT_SEPARATOR = " · "

/**
 * The investments page of [InvestmentDetailViewModel]: tapping a movement calls [onOpenExpense] with the expense id
 * (the caller opens the expense editor).
 */
@Composable
fun InvestmentDetailScreen(
    onBack: () -> Unit,
    onOpenExpense: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InvestmentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        InvestmentDetailUiState.Loading -> Placeholder(onBack, modifier, message = null)
        InvestmentDetailUiState.Error ->
            Placeholder(onBack, modifier, message = stringResource(R.string.error_generic_fallback))
        is InvestmentDetailUiState.Content ->
            InvestmentContent(
                state = current,
                onBack = onBack,
                onSelectMonth = { viewModel.onSelectMonth(current.months[it].month) },
                onOpenExpense = onOpenExpense,
                modifier = modifier,
            )
    }
}

@Composable
private fun Placeholder(
    onBack: () -> Unit,
    modifier: Modifier,
    message: String?,
) {
    LargeTitleScaffold(title = stringResource(R.string.investments_title), onBack = onBack, modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().padding(it)) { message?.let { text -> EmptyState(title = text) } }
    }
}

@Composable
private fun InvestmentContent(
    state: InvestmentDetailUiState.Content,
    onBack: () -> Unit,
    onSelectMonth: (Int) -> Unit,
    onOpenExpense: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val bars =
        state.months.map {
            val label = formatShortMonthName(it.month, locale)
            MonthlyBar(label, it.totalCents, "$label ${formatAmountCents(it.totalCents)}")
        }
    val selectedLabel =
        if (state.isCurrentMonth) {
            stringResource(R.string.investments_this_month)
        } else {
            formatShortMonthName(state.selected.month, locale)
        }
    val allTimeLabel = stringResource(R.string.investments_all_time)
    val summary =
        listOf(
            "$selectedLabel ${formatAmountCents(state.selected.totalCents)}",
            "${state.year} ${formatAmountCents(state.yearCents)}",
            "$allTimeLabel ${formatAmountCents(state.allTimeCents)}",
        ).joinToString(CONTEXT_SEPARATOR)
    DetailScaffold(
        title = stringResource(R.string.investments_title),
        amount = formatAmountCents(state.selected.totalCents),
        bars = bars,
        selectedIndex = state.selectedIndex,
        onBack = onBack,
        modifier = modifier,
        contextLine = summary,
        onBarClick = onSelectMonth,
    ) {
        if (state.movements.isEmpty()) {
            item(key = "empty") { EmptyState(title = stringResource(R.string.investments_no_movements)) }
        } else {
            item(key = "instruments_title") { SectionTitle(title = stringResource(R.string.investments_by_instrument)) }
            items(state.selected.instruments, key = { "instrument_${it.key.value}" }) { instrument ->
                SettingsRow(
                    title = instrument.labelIn(state),
                    value = formatAmountCents(instrument.cents),
                )
            }
            item(key = "movements_title") { SectionTitle(title = stringResource(R.string.investments_movements)) }
            items(state.movements, key = { it.id }) { expense ->
                ExpenseRow(expense, onClick = { onOpenExpense(expense.id) })
            }
        }
        item(key = "end") { Box(Modifier.padding(bottom = Dimens.Space32)) }
    }
}

/** The instrument's own name, else the name of its category (found on the month's movements). */
@Composable
private fun InstrumentAmount.labelIn(state: InvestmentDetailUiState.Content): String =
    label ?: state.movements
        .firstOrNull { it.category.id == categoryId }
        ?.category
        ?.displayName
        .orEmpty()

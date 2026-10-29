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
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.SalaryComponent
import com.joaoeoneves.fintrack.ui.common.ExpenseRow
import com.joaoeoneves.fintrack.ui.common.IncomeRow
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.formatPercent
import com.joaoeoneves.fintrack.ui.common.period.formatShortMonthName
import com.joaoeoneves.fintrack.ui.designsystem.DetailDelta
import com.joaoeoneves.fintrack.ui.designsystem.DetailScaffold
import com.joaoeoneves.fintrack.ui.designsystem.EmptyState
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.MonthlyBar
import com.joaoeoneves.fintrack.ui.plan.label
import com.joaoeoneves.fintrack.ui.theme.Dimens
import kotlin.math.abs

private const val CONTEXT_SEPARATOR = " · "

/** What a tap on a movement or on "Edit" does. */
data class DetailActions(
    val onOpenExpense: (String) -> Unit,
    val onOpenIncome: (String) -> Unit,
    val onEdit: (DetailEditTarget) -> Unit,
)

/** The detail page of a category or an income source (see [DetailViewModel]). */
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    actions: DetailActions,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        DetailUiState.Loading -> PlaceholderPage(onBack, modifier, message = null)
        DetailUiState.NotFound -> PlaceholderPage(onBack, modifier, message = stringResource(R.string.detail_not_found))
        is DetailUiState.Content ->
            DetailContent(
                state = current,
                onBack = onBack,
                onSelectMonth = { viewModel.onSelectMonth(current.months[it].month) },
                actions = actions,
                modifier = modifier,
            )
    }
}

@Composable
private fun PlaceholderPage(
    onBack: () -> Unit,
    modifier: Modifier,
    message: String?,
) {
    LargeTitleScaffold(title = "", onBack = onBack, modifier = modifier) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            message?.let { EmptyState(title = it) }
        }
    }
}

@Composable
private fun DetailContent(
    state: DetailUiState.Content,
    onBack: () -> Unit,
    onSelectMonth: (Int) -> Unit,
    actions: DetailActions,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val bars =
        state.months.map {
            val label = formatShortMonthName(it.month, locale)
            MonthlyBar(label, it.valueCents, "$label ${formatAmountCents(it.valueCents)}")
        }
    val delta =
        state.delta?.let {
            val arrow = if (it.percent >= 0) "▲" else "▼"
            val text = stringResource(R.string.detail_vs_average, "$arrow ${formatPercent(abs(it.percent), locale)}")
            // Spending up is bad, income up is good.
            DetailDelta(text, good = (it.percent >= 0) == state.isIncome)
        }
    DetailScaffold(
        title = titleOf(state.subject),
        amount = formatAmountCents(state.selected.valueCents),
        bars = bars,
        selectedIndex = state.selectedIndex,
        onBack = onBack,
        modifier = modifier,
        delta = delta,
        contextLine = contextOf(state.subject),
        menuActionLabel = state.editTarget?.let { stringResource(R.string.detail_edit) },
        onMenuAction = { state.editTarget?.let(actions.onEdit) },
        onBarClick = onSelectMonth,
    ) {
        if (state.movements.isEmpty()) {
            item(key = "empty") { EmptyState(title = stringResource(R.string.detail_no_movements)) }
        }
        items(state.movements, key = { it.id }) { transaction ->
            when (transaction) {
                is Expense ->
                    ExpenseRow(transaction, onClick = { actions.onOpenExpense(transaction.id) })
                is Income ->
                    IncomeRow(
                        transaction,
                        countsFor = state.shiftedIncomes[transaction.id],
                        onClick = { actions.onOpenIncome(transaction.id) },
                    )
            }
        }
        item(key = "end") { Box(Modifier.padding(bottom = Dimens.Space32)) }
    }
}

@Composable
private fun titleOf(subject: DetailSubject): String =
    when (subject) {
        is DetailSubject.OfCategory -> subject.category.displayName
        is DetailSubject.OfSource ->
            subject.component?.takeIf { it != SalaryComponent.EXTRA }?.label()
                ?: subject.label
                ?: stringResource(R.string.detail_source_unknown)
    }

@Composable
private fun contextOf(subject: DetailSubject): String? =
    when (subject) {
        is DetailSubject.OfCategory ->
            listOfNotNull(
                subject.envelopeName?.let { stringResource(R.string.detail_context_envelope, it) }
                    ?: stringResource(R.string.detail_context_no_envelope),
                subject.limitCents?.let { stringResource(R.string.detail_context_limit, formatAmountCents(it)) },
            ).joinToString(CONTEXT_SEPARATOR)
        is DetailSubject.OfSource ->
            listOfNotNull(
                stringResource(R.string.detail_context_next_month).takeIf { subject.shifted },
                subject.expectedCents?.let { stringResource(R.string.detail_context_expected, formatAmountCents(it)) },
            ).takeIf { it.isNotEmpty() }?.joinToString(CONTEXT_SEPARATOR)
    }

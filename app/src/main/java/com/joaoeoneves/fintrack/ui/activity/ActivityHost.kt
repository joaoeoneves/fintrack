package com.joaoeoneves.fintrack.ui.activity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.SettingsIconButton
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.expense.list.ExpenseListScreen
import com.joaoeoneves.fintrack.ui.income.list.IncomeListScreen
import com.joaoeoneves.fintrack.ui.theme.Dimens

private val ExpensesOrIncome = listOf(false, true)

/**
 * The Activity tab (interim, replaced by a unified list in pass A2): an Expenses | Income switch
 * over the existing list screens, plus the Settings icon. The switch survives configuration changes
 * and process death; [showIncomeFirst] only picks the initial segment.
 */
@Composable
fun ActivityHost(
    showIncomeFirst: Boolean,
    onOpenExpense: (String) -> Unit,
    onOpenIncome: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showIncome by rememberSaveable { mutableStateOf(showIncomeFirst) }
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Dimens.ScreenPadding, end = Dimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentedTextTabs(
                options = ExpensesOrIncome,
                selected = showIncome,
                label = {
                    stringResource(if (it) R.string.dashboard_income_label else R.string.dashboard_expenses_label)
                },
                onSelect = { showIncome = it },
                modifier = Modifier.weight(1f),
            )
            SettingsIconButton(onClick = onOpenSettings)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().testTag(TestTags.ACTIVITY_LIST)) {
            if (showIncome) {
                IncomeListScreen(onBack = null, onOpenIncome = onOpenIncome)
            } else {
                ExpenseListScreen(onBack = null, onOpenExpense = onOpenExpense)
            }
        }
    }
}

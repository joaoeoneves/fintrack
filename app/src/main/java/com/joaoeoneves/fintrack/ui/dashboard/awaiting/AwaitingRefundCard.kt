package com.joaoeoneves.fintrack.ui.dashboard.awaiting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.OwedRefund
import com.joaoeoneves.fintrack.ui.common.ExpenseRow
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/**
 * Home's "Awaiting refund" card: what is still owed to the user (all time, not tied to the viewed period), the total
 * in the header and, per expense, what is left plus a "Record refund" action that opens the prefilled refund form.
 */
@Composable
fun AwaitingRefundCard(
    model: AwaitingRefundModel,
    modifier: Modifier = Modifier,
) {
    val state = model.state as? AwaitingRefundUiState.Visible ?: return
    Column(modifier = modifier.fillMaxWidth().testTag(TestTags.AWAITING_REFUND)) {
        SectionTitle(title = stringResource(R.string.home_awaiting_refund_title))
        Text(
            text = stringResource(R.string.home_awaiting_refund_total, formatAmountCents(state.totalRemainingCents)),
            style = MaterialTheme.typography.bodySmall,
            color = FinTrackTheme.colors.textSecondary,
        )
        state.items.forEach { owed -> OwedRow(owed, model) }
        val more = state.total - state.items.size
        if (more > 0) {
            Text(
                text = stringResource(R.string.home_review_more, more),
                style = MaterialTheme.typography.bodyMedium,
                color = FinTrackTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = Dimens.Space8),
            )
        }
    }
}

@Composable
private fun OwedRow(
    owed: OwedRefund,
    model: AwaitingRefundModel,
) {
    Column {
        ExpenseRow(
            expense = owed.expense,
            onClick = { model.onOpenExpense(owed.expense.id) },
            showDate = true,
            refundedPercent = owed.percent,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.Space16),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_awaiting_refund_remaining, formatAmountCents(owed.remainingCents)),
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.textSecondary,
            )
            GhostButton(
                text = stringResource(R.string.home_awaiting_refund_record),
                onClick = { model.onRecordRefund(owed) },
            )
        }
    }
}

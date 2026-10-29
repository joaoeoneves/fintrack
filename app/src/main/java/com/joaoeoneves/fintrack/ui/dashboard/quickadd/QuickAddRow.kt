package com.joaoeoneves.fintrack.ui.dashboard.quickadd

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.QuickAddSuggestion
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.CategoryChip
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.theme.Dimens

/** Home's "Quick add" row: one chip per repeated expense; tapping opens the new-expense form prefilled with it. */
@Composable
fun QuickAddRow(
    suggestions: List<QuickAddSuggestion>,
    onQuickAdd: (QuickAddSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().testTag(TestTags.QUICK_ADD)) {
        SectionTitle(title = stringResource(R.string.home_quick_add_title))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            suggestions.forEach { suggestion ->
                val amount = formatAmountCents(suggestion.amountCents)
                val description =
                    stringResource(
                        R.string.home_quick_add_chip_cd,
                        suggestion.name,
                        formatAmountCents(suggestion.amountCents, typographic = false),
                    )
                CategoryChip(
                    label = "${suggestion.name} · $amount",
                    selected = false,
                    onClick = { onQuickAdd(suggestion) },
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
        }
    }
}

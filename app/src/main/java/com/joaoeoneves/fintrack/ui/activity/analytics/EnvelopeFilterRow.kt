package com.joaoeoneves.fintrack.ui.activity.analytics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.CategoryChip
import com.joaoeoneves.fintrack.ui.designsystem.LegendDot
import com.joaoeoneves.fintrack.ui.theme.Dimens

/** Test tag of the "All" envelope chip; the per-envelope chips use `envelope_chip_<id>`. */
const val ENVELOPE_CHIP_ALL_TAG = "envelope_chip_all"

/**
 * Horizontally scrollable, single-select envelope filter: an "All" chip (selected when [selected] is null)
 * followed by one chip per entry of [chips], labelled "Name · amount" with the colour dot of its bar segment
 * ([dotColors], by index).
 */
@Composable
fun EnvelopeFilterRow(
    chips: List<EnvelopeChip>,
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    dotColors: List<Color> = emptyList(),
) {
    val noEnvelope = stringResource(R.string.plan_group_other)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
    ) {
        CategoryChip(
            label = stringResource(R.string.activity_envelope_all),
            selected = selected == null,
            onClick = { onSelected(null) },
            modifier = Modifier.testTag(ENVELOPE_CHIP_ALL_TAG),
        )
        chips.forEachIndexed { index, chip ->
            val name = chip.name ?: noEnvelope
            val spoken =
                stringResource(
                    R.string.cd_envelope_chip,
                    name,
                    formatAmountCents(chip.spentCents, typographic = false),
                )
            CategoryChip(
                label = "$name · ${formatAmountCents(chip.spentCents)}",
                selected = selected == chip.id,
                leading = dotColors.getOrNull(index)?.let { dot -> { LegendDot(dot) } },
                onClick = { onSelected(chip.id) },
                modifier =
                    Modifier
                        .testTag("envelope_chip_${chip.id}")
                        .semantics { contentDescription = spoken },
            )
        }
    }
}

package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.PotsSummary
import com.joaoeoneves.fintrack.ui.common.PotOverflowRows
import com.joaoeoneves.fintrack.ui.common.PotRow
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup

/** The "Pots" summary: the base first (what is safe to spend), then every earmarked pot. */
@Composable
fun PotsCard(
    pots: PotsSummary,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.plan_pots_title), modifier = modifier) {
        PotRow(pot = pots.base)
        PotOverflowRows(pots = pots.pots)
        pots.pots.forEach { pot ->
            Hairline()
            PotRow(pot = pot)
        }
    }
}

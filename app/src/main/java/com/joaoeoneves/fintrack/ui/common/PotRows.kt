package com.joaoeoneves.fintrack.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Pot
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import kotlin.math.abs

/** The pot's display name: its group's or category's name, "Base" for the shared base. */
@Composable
fun Pot.potName(): String =
    when {
        group != null -> group.name
        category != null -> category.displayName
        target == null -> stringResource(R.string.plan_pot_base)
        else -> stringResource(R.string.plan_pot)
    }

/**
 * One compact line of a pot, e.g. "Food · €55.12 left of €230.12"; in the negative color once it is
 * overrun. Clickable only when [onClick] is given.
 */
@Composable
fun PotRow(
    pot: Pot,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val name = pot.potName()
    val free = pot.freeCents
    val text =
        if (free >= 0) {
            stringResource(
                R.string.plan_pot_left,
                name,
                formatAmountCents(free),
                formatAmountCents(pot.expectedIncomeCents),
            )
        } else {
            stringResource(
                R.string.plan_pot_over,
                name,
                formatAmountCents(abs(free)),
                formatAmountCents(pot.expectedIncomeCents),
            )
        }
    val description = stringResource(R.string.cd_pot, text)
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (free >= 0) FinTrackTheme.colors.textSecondary else FinTrackTheme.colors.negative,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .fillMaxWidth()
                .then(clickable)
                .heightIn(min = Dimens.SettingsRowMinHeight)
                .padding(vertical = Dimens.Space8)
                .semantics { contentDescription = description },
    )
}

/** For every overrun pot, "−€X Food overflow": what it spills into the base. */
@Composable
fun PotOverflowRows(
    pots: List<Pot>,
    modifier: Modifier = Modifier,
) {
    val overrun = pots.filter { it.freeCents < 0 }
    if (overrun.isEmpty()) return
    Column(modifier = modifier) {
        overrun.forEach { pot ->
            Text(
                text =
                    stringResource(
                        R.string.plan_pot_overflow_row,
                        formatAmountCents(abs(pot.freeCents)),
                        stringResource(R.string.plan_pot_overflow, pot.potName()),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.negative,
            )
        }
    }
}

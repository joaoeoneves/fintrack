package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** The one envelope row of the Plan (Month and Year). Tappable when [onClick] is set; [footer] is for notes. */
@Composable
fun EnvelopeRow(
    data: EnvelopeRowData,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickModifier =
        if (onClick == null) {
            Modifier
        } else {
            Modifier.pressScale(interactionSource).clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
        }
    Column(modifier = modifier.fillMaxWidth().then(clickModifier).padding(vertical = Dimens.Space12)) {
        EnvelopeRowHeader(data)
        if (data.progress != null) {
            ThinProgressBar(
                progress = data.progress,
                isOver = data.isOver,
                modifier = Modifier.padding(top = Dimens.Space8),
            )
        }
        if (data.caption != null) {
            Text(
                text = data.caption,
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.textSecondary,
                modifier = Modifier.padding(top = Dimens.Space4),
            )
        }
        if (data.overText != null) {
            Text(
                text = data.overText,
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.negative,
                modifier = Modifier.padding(top = Dimens.Space4),
            )
        }
        footer?.invoke()
    }
}

@Composable
private fun EnvelopeRowHeader(data: EnvelopeRowData) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = data.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (data.amountText != null) {
            val semantics = data.amountSemantics
            Text(
                text = data.amountText,
                style = Amount,
                color = if (data.isOver) FinTrackTheme.colors.negative else MaterialTheme.colorScheme.onSurface,
                modifier =
                    if (semantics != null) {
                        Modifier.clearAndSetSemantics { text = AnnotatedString(semantics) }
                    } else {
                        Modifier
                    },
            )
        }
    }
}

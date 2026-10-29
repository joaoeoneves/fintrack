package com.joaoeoneves.fintrack.ui.common.period

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.theme.Dimens

/**
 * The one period header of Plan (Month and Year), Income, Home and Activity: previous/next arrows around a [label]
 * slot (a menu button for months, plain text for a year). Centered, the arrows sit at both ends and the label in
 * the middle; start-aligned, the arrows hug the label.
 */
@Composable
fun PeriodHeader(
    navigation: PeriodNavigation,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    label: @Composable () -> Unit,
) {
    val centered = alignment == Alignment.CenterHorizontally
    val arrangement =
        when (alignment) {
            Alignment.Start -> Arrangement.Start
            Alignment.End -> Arrangement.End
            else -> Arrangement.Center
        }
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Dimens.Space4),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigation.onPrevious?.let {
            LabeledIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription =
                    stringResource(if (navigation.isYear) R.string.cd_previous_year else R.string.cd_previous_month),
                onClick = it,
                enabled = navigation.canGoPrevious,
            )
        }
        Box(
            modifier = if (centered) Modifier.weight(1f) else Modifier.weight(1f, fill = false),
            contentAlignment = Alignment.Center,
        ) { label() }
        navigation.onNext?.let {
            LabeledIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription =
                    stringResource(if (navigation.isYear) R.string.cd_next_year else R.string.cd_next_month),
                onClick = it,
                enabled = navigation.canGoNext,
            )
        }
    }
}

/** The plain-text label of a [PeriodHeader] without a menu (a year). */
@Composable
fun PeriodHeaderText(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
}

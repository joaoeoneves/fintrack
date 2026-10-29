@file:Suppress("MatchingDeclarationName") // DetailScaffold is the composable; DetailDelta is its data holder.

package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.core.designsystem.R
import com.joaoeoneves.fintrack.ui.theme.AmountLarge
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** The "▲ 12 % vs média" line: [text] already formatted; [good] picks the positive or the negative colour. */
data class DetailDelta(
    val text: String,
    val good: Boolean,
)

/**
 * A detail page: [title] with an optional ⋮ menu (one [menuActionLabel] action, e.g. "Edit"), the big [amount] with an
 * optional [delta], the [bars] chart (tap selects a month through [onBarClick]; a negative [selectedIndex] highlights
 * none), a [contextLine] and then the list built by [list]. [header] (e.g. the period selector) sits above the amount.
 */
@Suppress("LongParameterList") // page slots; each one is independently optional
@Composable
fun DetailScaffold(
    title: String,
    amount: String,
    bars: List<MonthlyBar>,
    selectedIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    delta: DetailDelta? = null,
    contextLine: String? = null,
    menuActionLabel: String? = null,
    onMenuAction: () -> Unit = {},
    onBarClick: (Int) -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    list: LazyListScope.() -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    LargeTitleScaffold(
        title = title,
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (menuActionLabel != null) {
                Box {
                    FinTrackIconButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options),
                        onClick = { menuOpen = true },
                    )
                    FinTrackDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        FinTrackMenuItem(
                            text = menuActionLabel,
                            onClick = {
                                menuOpen = false
                                onMenuAction()
                            },
                        )
                    }
                }
            }
        },
    ) { padding: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            header?.invoke()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space16),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space16),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space4)) {
                        Text(text = amount, style = AmountLarge)
                        delta?.let {
                            Text(
                                text = it.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (it.good) FinTrackTheme.colors.positive else FinTrackTheme.colors.negative,
                            )
                        }
                    }
                }
                item {
                    val highlighted = setOfNotNull(selectedIndex.takeIf { it >= 0 })
                    MonthlyBars(bars = bars, highlighted = highlighted, onBarClick = onBarClick)
                }
                contextLine?.let {
                    item {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = FinTrackTheme.colors.textSecondary,
                        )
                    }
                }
                list()
            }
        }
    }
}

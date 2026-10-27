package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private val SectionTitleMinHeight = 40.dp

/**
 * The one section heading style of the app: titleMedium, SemiBold, onSurface, with an optional
 * [trailing] slot (typically a [SectionAction]). Pass [onTitleClick] to make the title itself
 * tappable. The space above a section is the caller's job (32dp between dashboard sections).
 */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = SectionTitleMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val titleModifier =
            if (onTitleClick != null) {
                Modifier.clickable(role = Role.Button, onClick = onTitleClick)
            } else {
                Modifier
            }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).then(titleModifier).padding(vertical = Dimens.Space8),
        )
        trailing?.invoke()
    }
}

/**
 * Quiet trailing link of a [SectionTitle] ("See all", "Edit plan", "Manage"). When
 * [contentDescription] is set it replaces the visible [text] for accessibility.
 */
@Composable
fun SectionAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = SectionTitleMinHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = Dimens.Space16),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = FinTrackTheme.colors.textSecondary,
            modifier =
                if (contentDescription != null) {
                    Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
        )
    }
}

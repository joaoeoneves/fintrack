package com.joaoeoneves.fintrack.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private data class HowItWorksSection(
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
)

private val Sections =
    listOf(
        HowItWorksSection(R.string.how_it_works_categories_title, R.string.how_it_works_categories_body),
        HowItWorksSection(R.string.how_it_works_groups_title, R.string.how_it_works_groups_body),
        HowItWorksSection(R.string.how_it_works_recurring_title, R.string.how_it_works_recurring_body),
        HowItWorksSection(R.string.how_it_works_review_title, R.string.how_it_works_review_body),
    )

/** A static page explaining categories, groups, recurring items and "To review". */
@Composable
fun HowItWorksScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LargeTitleScaffold(
        title = stringResource(R.string.how_it_works_title),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.ScreenPadding)
                    .padding(bottom = Dimens.Space32),
        ) {
            Sections.forEach { section ->
                SectionTitle(title = stringResource(section.title))
                Text(
                    text = stringResource(section.body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinTrackTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = Dimens.Space16),
                )
            }
        }
    }
}

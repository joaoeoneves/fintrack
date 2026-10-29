package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.EmploymentSector
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs

/** Where the user works (wording and set-up presets only; the math does not depend on it). */
@Composable
internal fun SectorSection(
    form: SalaryForm,
    onChange: (SalaryForm) -> Unit,
) {
    SalaryGroup(stringResource(R.string.salary_group_sector)) {
        SegmentedTextTabs(
            options = EmploymentSector.entries,
            selected = form.effectiveSector,
            label = { stringResource(sectorLabel(it)) },
            onSelect = { onChange(form.copy(sector = it)) },
        )
    }
}

internal fun sectorLabel(sector: EmploymentSector): Int =
    if (sector == EmploymentSector.PUBLIC) R.string.salary_sector_public else R.string.salary_sector_private

/** The social-security field label: "SS/CGA" for the public sector. */
internal fun ssFieldLabel(sector: EmploymentSector): Int =
    if (sector == EmploymentSector.PUBLIC) R.string.salary_field_ss_public else R.string.salary_field_ss

internal fun ssHint(sector: EmploymentSector): Int =
    if (sector == EmploymentSector.PUBLIC) R.string.salary_ss_hint_public else R.string.salary_ss_hint

package com.joaoeoneves.fintrack.ui.plan.salary.setup

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.EmploymentSector
import com.joaoeoneves.fintrack.domain.model.MealPayment
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryForm
import java.time.YearMonth

/** Everything the wizard asks, so a process death in the middle does not lose it. */
internal data class SetupDraft(
    val form: SalaryForm,
    val mealAnswer: MealAnswer,
    val payDay: PayDayDraft,
    val irsJovem: Boolean,
    val effectiveFrom: YearMonth?,
)

private const val KEY_DRAFT = "draft_form"
private const val SEP = "|"

/** Writes [draft] as primitives (the wizard never has extras, so they are not stored). */
internal fun SavedStateHandle.saveDraft(draft: SetupDraft) {
    val form = draft.form
    this[KEY_DRAFT] =
        listOf(
            form.mode.name,
            form.base,
            form.spreadPpm,
            form.irsBase,
            form.irsSubsidies,
            form.ss,
            form.exempt,
            form.netMonthly,
            form.netHoliday,
            form.netChristmas,
            form.meal,
            form.subsidyMonths.joinToString(","),
            form.sector?.name.orEmpty(),
            form.mealPayment.name,
            form.otherDeductions,
            draft.mealAnswer.name,
            draft.payDay.fixed,
            draft.payDay.dayText,
            draft.payDay.earlierIfClosed,
            draft.irsJovem,
            draft.effectiveFrom?.toString().orEmpty(),
        ).joinToString(SEP) { it.toString().replace(SEP, "") }
}

/** The stored draft, or `null` when none was stored or it does not read back. */
internal fun SavedStateHandle.loadDraft(): SetupDraft? =
    get<String>(KEY_DRAFT)?.let { runCatching { parseDraft(it.split(SEP)) }.getOrNull() }

@Suppress("MagicNumber") // positions mirror saveDraft
private fun parseDraft(p: List<String>): SetupDraft? {
    if (p.size != 21) return null
    val form =
        SalaryForm(
            mode = SalaryInputMode.valueOf(p[0]),
            base = p[1].toLong(),
            spreadPpm = p[2].toInt(),
            irsBase = p[3],
            irsSubsidies = p[4],
            ss = p[5],
            exempt = p[6],
            netMonthly = p[7].toLong(),
            netHoliday = p[8].toLong(),
            netChristmas = p[9].toLong(),
            meal = p[10].toLong(),
            subsidyMonths = p[11].split(",").filter { it.isNotEmpty() }.map { it.toInt() },
            sector = EmploymentSector.entries.firstOrNull { it.name == p[12] },
            mealPayment = MealPayment.valueOf(p[13]),
            otherDeductions = p[14],
        )
    return SetupDraft(
        form = form,
        mealAnswer = MealAnswer.valueOf(p[15]),
        payDay = PayDayDraft(fixed = p[16].toBoolean(), dayText = p[17], earlierIfClosed = p[18].toBoolean()),
        irsJovem = p[19].toBoolean(),
        effectiveFrom = p[20].takeIf { it.isNotEmpty() }?.let(YearMonth::parse),
    )
}

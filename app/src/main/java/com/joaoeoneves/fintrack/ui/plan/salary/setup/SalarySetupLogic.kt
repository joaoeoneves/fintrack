package com.joaoeoneves.fintrack.ui.plan.salary.setup

import com.joaoeoneves.fintrack.domain.model.EmploymentSector
import com.joaoeoneves.fintrack.domain.model.MealPayment
import com.joaoeoneves.fintrack.domain.model.SPREAD_NONE_PPM
import com.joaoeoneves.fintrack.ui.plan.salary.Parsed
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryForm
import com.joaoeoneves.fintrack.ui.plan.salary.effectiveSector
import com.joaoeoneves.fintrack.ui.plan.salary.parsePercentPpm

/** The steps of the salary set-up wizard, in order. */
enum class SalarySetupStep { SECTOR, PAY, SUBSIDIES, MEAL, SUMMARY }

/** The steps the wizard walks through for [sector] (every sector asks every question; only the presets differ). */
@Suppress("UNUSED_PARAMETER")
fun setupStepsFor(sector: EmploymentSector?): List<SalarySetupStep> = SalarySetupStep.entries

/** The answer to "Do you get a meal allowance?". */
enum class MealAnswer { NO, CARD, WITH_SALARY }

/** The pay-day row of the summary step: the last working day, or a fixed day with the weekend/holiday rule. */
data class PayDayDraft(
    val fixed: Boolean = false,
    val dayText: String = "",
    val earlierIfClosed: Boolean = false,
) {
    /** The fixed day, or `null` for the last working day or while the text is not a day of the month. */
    val day: Int? get() = if (fixed) dayText.toIntOrNull()?.takeIf { it in MIN_DAY..MAX_DAY } else null

    /** Whether the draft can be saved: a fixed day must be filled in. */
    val isValid: Boolean get() = !fixed || day != null
}

private const val MIN_DAY = 1
private const val MAX_DAY = 31

/** The pay-day answer a sector starts with: the public sector pays on a fixed day the user still has to fill in. */
fun payDayPresetFor(sector: EmploymentSector): PayDayDraft =
    if (sector == EmploymentSector.PUBLIC) PayDayDraft(fixed = true, earlierIfClosed = true) else PayDayDraft()

/** The meal answer a sector starts with. */
fun mealAnswerPresetFor(sector: EmploymentSector): MealAnswer =
    if (sector == EmploymentSector.PUBLIC) MealAnswer.WITH_SALARY else MealAnswer.CARD

/**
 * This form with [sector]'s presets: private pays no twelfths (subsidies in June and December, in one go; meal on a
 * card); public pays no twelfths, subsidies in June and November and meal with the salary. The amounts and
 * rates already typed stay.
 */
fun SalaryForm.withSectorPreset(sector: EmploymentSector): SalaryForm =
    when (sector) {
        EmploymentSector.PRIVATE ->
            copy(
                sector = sector,
                spreadPpm = SPREAD_NONE_PPM,
                subsidyMonths = listOf(PRIVATE_HOLIDAY_MONTH, PRIVATE_CHRISTMAS_MONTH),
                mealPayment = MealPayment.CARD,
                otherDeductions = "",
            )
        EmploymentSector.PUBLIC ->
            copy(
                sector = sector,
                spreadPpm = SPREAD_NONE_PPM,
                subsidyMonths = listOf(PUBLIC_HOLIDAY_MONTH, PUBLIC_SECOND_SUBSIDY_MONTH),
                mealPayment = MealPayment.WITH_SALARY,
                otherDeductions = "",
            )
    }

private const val PRIVATE_HOLIDAY_MONTH = 6
private const val PRIVATE_CHRISTMAS_MONTH = 12
private const val PUBLIC_HOLIDAY_MONTH = 6
private const val PUBLIC_SECOND_SUBSIDY_MONTH = 11

/** What "Tenho ADSE" sets other deductions to: 3.5%. */
const val ADSE_PERCENT = "3.5"
private const val ADSE_PPM = 35_000

/** Whether the form's other deductions are exactly the ADSE rate (the "I have ADSE" switch is on). */
val SalaryForm.hasAdse: Boolean get() = (parsePercentPpm(otherDeductions) as? Parsed.Value)?.value == ADSE_PPM

/** The form with the ADSE switch turned [on] or off; the rate is written with [decimalSeparator] (both parse). */
fun SalaryForm.withAdse(
    on: Boolean,
    decimalSeparator: Char = '.',
): SalaryForm = copy(otherDeductions = if (on) ADSE_PERCENT.replace('.', decimalSeparator) else "")

/** The answers a sector change can affect, kept together so the change is one pure step. */
data class SectorAnswers(
    val form: SalaryForm,
    val mealAnswer: MealAnswer,
    val payDay: PayDayDraft,
)

/**
 * These answers after picking [sector]: its presets apply, but the meal answer, the pay day and the ADSE rate the
 * user has already changed stay; only the ones still at the previous sector's defaults follow the new sector.
 */
fun SectorAnswers.withSector(sector: EmploymentSector): SectorAnswers {
    val previous = form.effectiveSector
    val answer = if (mealAnswer == mealAnswerPresetFor(previous)) mealAnswerPresetFor(sector) else mealAnswer
    val day = if (payDay == payDayPresetFor(previous)) payDayPresetFor(sector) else payDay
    val preset = form.withSectorPreset(sector).copy(otherDeductions = form.otherDeductions)
    return SectorAnswers(preset.withMealAnswer(answer), answer, day)
}

/** The form with the meal answer applied: "no" clears the rate, either "yes" sets how it is paid. */
fun SalaryForm.withMealAnswer(answer: MealAnswer): SalaryForm =
    when (answer) {
        MealAnswer.NO -> copy(meal = 0L)
        MealAnswer.CARD -> copy(mealPayment = MealPayment.CARD)
        MealAnswer.WITH_SALARY -> copy(mealPayment = MealPayment.WITH_SALARY)
    }

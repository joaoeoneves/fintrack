package com.joaoeoneves.fintrack.data.local

import kotlinx.serialization.Serializable

/** Version written into [LocalDataDocument.schemaVersion]; a file with a higher one is not read. */
internal const val LOCAL_SCHEMA_VERSION = 22

/**
 * The whole guest-mode data set, stored as one JSON file. Enums are stored as strings and dates as
 * ISO-8601 instants (lossless to the nanosecond). [revision] increases on every write.
 */
@Serializable
data class LocalDataDocument(
    val schemaVersion: Int = LOCAL_SCHEMA_VERSION,
    val revision: Long = 0,
    val expenses: List<LocalExpense> = emptyList(),
    val income: List<LocalIncome> = emptyList(),
    val budgets: List<LocalBudget> = emptyList(),
    /** Overrides of built-in categories plus user categories; `builtIn` is derived from the id. */
    val categories: List<LocalCategory> = emptyList(),
    /** Expected-income lines of the monthly plan (absent in schema v1 files). */
    val plannedIncome: List<LocalPlannedIncome> = emptyList(),
    /** Recurring bill/income templates (absent in schema v1 and v2 files). */
    val recurring: List<LocalRecurringItem> = emptyList(),
    /** Per-month plan settings (absent in schema v1 to v3 files; `daysOff` since v6). */
    val monthSettings: List<LocalMonthSettings> = emptyList(),
    /** Category groups (absent in schema v1 to v4 files). */
    val categoryGroups: List<LocalCategoryGroup> = emptyList(),
    /** The salary profile singleton (absent in schema v1 to v7 files). */
    val salaryProfile: LocalSalaryProfile? = null,
    /** Savings goals (absent in schema v1 to v8 files). */
    val goals: List<LocalSavingsGoal> = emptyList(),
    /** The automation settings singleton (absent in schema v1 to v11 files). */
    val automation: LocalAutomation? = null,
    /** Normalized names of the dismissed "make it recurring?" suggestions (absent in schema v1 to v21 files). */
    val dismissedRecurringSuggestions: List<String> = emptyList(),
) {
    /** Whether there is no user data at all. */
    val isEmpty: Boolean get() =
        expenses.isEmpty() &&
            income.isEmpty() &&
            budgets.isEmpty() &&
            categories.isEmpty() &&
            plannedIncome.isEmpty() &&
            recurring.isEmpty() &&
            monthSettings.isEmpty() &&
            categoryGroups.isEmpty() &&
            salaryProfile == null &&
            goals.isEmpty() &&
            automation == null
}

@Serializable
data class LocalExpense(
    val id: String,
    val name: String,
    val amountCents: Long,
    val categoryId: String,
    /** ISO-8601 instant, e.g. `2026-01-31T10:15:30.123456789Z`. */
    val date: String,
    val note: String? = null,
    val isRefund: Boolean = false,
    /** Paid from the meal card (schema v20; absent = bank). */
    val paidWithMealCard: Boolean = false,
    /** Recorded automatically and not yet reviewed (schema v22; absent = reviewed). */
    val needsReview: Boolean = false,
)

@Serializable
data class LocalIncome(
    val id: String,
    val source: String,
    val amountCents: Long,
    val date: String,
    val note: String? = null,
    /** Recorded automatically and not yet reviewed (schema v22; absent = reviewed). */
    val needsReview: Boolean = false,
)

@Serializable
data class LocalBudget(
    val categoryId: String,
    val limitCents: Long,
    /** Month numbers 1..12 the limit does not apply to (absent in schema v1 to v4 files). */
    val skipMonths: List<Int> = emptyList(),
    /** ISO `yyyy-MM`: first plan month the limit applies in; absent = always (schema v19). */
    val startMonth: String? = null,
    /** Limit history (schema v19); [limitCents] is the latest. */
    val versions: List<LocalAmountVersion> = emptyList(),
)

/** An amount that applies from plan month [from] (ISO `yyyy-MM`); a null amount has the item's own meaning. */
@Serializable
data class LocalAmountVersion(
    val from: String,
    val amountCents: Long? = null,
)

@Serializable
data class LocalPlannedIncome(
    val id: String,
    val label: String,
    val amountCents: Long,
    val order: Int,
    /** An [com.joaoeoneves.fintrack.domain.model.IncomeBasis] name (absent in older files: FIXED). */
    val basis: String = "FIXED",
    /** An [com.joaoeoneves.fintrack.domain.model.IncomeFrequency] name (absent in older files: MONTHLY). */
    val frequency: String = "MONTHLY",
    val monthOfYear: Int? = null,
    /** ISO `yyyy-MM`, set for one-off lines. */
    val yearMonth: String? = null,
    val skipMonths: List<Int> = emptyList(),
    /** Set on lines generated from the salary profile (schema v8). */
    val managedKey: String? = null,
    /** Record the line's income on its pay date (schema v16; absent = off). */
    val autoRecord: Boolean = false,
    /** Fixed pay day 1..31 (schema v16); absent = the month's last working day. */
    val payDay: Int? = null,
    /** A fixed pay day on a weekend or holiday moves to the previous working day (schema v20; absent = off). */
    val payDayEarlierIfClosed: Boolean = false,
    /** First plan month the line applies in, ISO `yyyy-MM`; absent = always (schema v19). */
    val startMonth: String? = null,
    /** Amount history (schema v19); [amountCents] is the latest. */
    val versions: List<LocalAmountVersion> = emptyList(),
)

/**
 * A month's manual working-days count and/or days off; [month] is ISO `yyyy-MM` and doubles as the record id.
 * [workingDays] is absent in a record that only holds days off, [daysOff] absent (0) in files before schema v6.
 */
@Serializable
data class LocalMonthSettings(
    val month: String,
    val workingDays: Int? = null,
    val daysOff: Int = 0,
)

@Serializable
data class LocalCategory(
    val id: String,
    val name: String? = null,
    val iconKey: String,
    val colorKey: String,
    /** A [com.joaoeoneves.fintrack.domain.model.CategoryKind] name. */
    val kind: String,
    val order: Int,
    val archived: Boolean = false,
    /** Id of the [LocalCategoryGroup] this category is filed under (absent in schema v1 to v4 files). */
    val groupId: String? = null,
    /** New expenses in this category default to the meal card (schema v20; absent = off). */
    val mealCardByDefault: Boolean = false,
)

/**
 * An envelope (kept under its pre-envelope name): a named bucket of categories; [order] is its position in the
 * list. [amountCents] is its fixed monthly amount and [fundedByLineId] the income line paying for it (schema v14).
 */
@Serializable
data class LocalCategoryGroup(
    val id: String,
    val name: String,
    val order: Int,
    val amountCents: Long? = null,
    val fundedByLineId: String? = null,
    /** First plan month the envelope exists in, ISO `yyyy-MM`; absent = always (schema v19). */
    val startMonth: String? = null,
    /** Amount history (schema v19); a null amount = sum of categories. */
    val versions: List<LocalAmountVersion> = emptyList(),
)

/**
 * A savings goal (schema v9): [targetType] is a [com.joaoeoneves.fintrack.domain.model.GoalTargetType] name, dates
 * are ISO `yyyy-MM-dd`, [linkedRecurringIds] the recurring items whose generated expenses count towards it and
 * [recurringId] (schema v15) the one a savings plan owns.
 */
@Serializable
data class LocalSavingsGoal(
    val id: String,
    val name: String,
    val targetCents: Long,
    val targetType: String = "ANNUAL",
    val startDate: String,
    val targetDate: String? = null,
    val initialCents: Long = 0L,
    val linkedRecurringIds: List<String> = emptyList(),
    val linkedCategoryId: String? = null,
    val archived: Boolean = false,
    val order: Int = 0,
    val recurringId: String? = null,
)

/**
 * The automation settings: [lineThrough] (schema v16) maps a planned income line id to the ISO `yyyy-MM` cursor of
 * its automatic recording. The old single payday switch (schema v12) is no longer read; old backups still decode.
 */
@Serializable
data class LocalAutomation(
    val lineThrough: Map<String, String> = emptyMap(),
    /** Schema v18: whether the salary pays the next month's plan; absent = not chosen (follows the profile). */
    val salaryPaysNextMonth: Boolean? = null,
)

/**
 * The salary profile (schema v8): rates in ppm, amounts in cents, the input mode as a
 * [com.joaoeoneves.fintrack.domain.model.SalaryInputMode] name. Absent fields take the domain defaults.
 */
@Serializable
data class LocalSalaryProfile(
    val inputMode: String = "GROSS",
    val baseGrossCents: Long? = null,
    val subsidySpreadPpm: Int = 500_000,
    val irsBasePpm: Int = 0,
    val irsSubsidiesPpm: Int? = null,
    val ssPpm: Int = 110_000,
    val irsJovemExemptPpm: Int = 0,
    val netMonthlyCents: Long? = null,
    val netHolidayLumpCents: Long? = null,
    val netChristmasLumpCents: Long? = null,
    val mealRatePerDayCents: Long? = null,
    /** Vacation days a year (schema v21); absent = the default of 22. */
    val vacationDaysPerYear: Int? = null,
    /** An [com.joaoeoneves.fintrack.domain.model.EmploymentSector] name (schema v20); absent = private. */
    val sector: String? = null,
    /** A [com.joaoeoneves.fintrack.domain.model.MealPayment] name (schema v20); absent = card. */
    val mealPayment: String = "CARD",
    /** ADSE / other deductions rate in ppm (schema v20). */
    val otherDeductionsPpm: Int = 0,
    val mealSkipMonths: List<Int> = emptyList(),
    val holidaySubsidyMonth: Int = 6,
    val christmasSubsidyMonth: Int = 12,
    val flexPpm: Int? = null,
    val flexAnnualCents: Long? = null,
    val flexFirstMonth: Int = 1,
    val flexWithdrawalFeePpm: Int = 0,
    val profitShareMultiplierPpm: Int? = null,
    val profitShareMonth: Int? = null,
    val profitShareMonths: List<Int> = emptyList(),
    val bonusCents: Long? = null,
    val bonusMonth: Int? = null,
    /** The user-defined extras (absent in schema v1 to v9 files, which carry the legacy fields above instead). */
    val extras: List<LocalSalaryExtra> = emptyList(),
)

@Serializable
data class LocalSalaryExtra(
    val id: String,
    val name: String,
    /** A `SalaryExtraAmountType` name. */
    val amountType: String,
    val amount: Long,
    val months: List<Int>,
    /** A `SalaryExtraDeductions` name. */
    val deductions: String = "NONE",
    val feePpm: Int = 0,
    /** A `SalaryExtraAmountScope` name; absent before schema v13. */
    val amountScope: String = "TOTAL",
)

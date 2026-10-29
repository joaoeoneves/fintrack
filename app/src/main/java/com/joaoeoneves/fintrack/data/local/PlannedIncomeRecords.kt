package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_DAY_OF_MONTH
import com.joaoeoneves.fintrack.domain.model.MAX_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.MAX_PLAN_LABEL_LENGTH
import com.joaoeoneves.fintrack.domain.model.MIN_DAY_OF_MONTH
import com.joaoeoneves.fintrack.domain.model.MIN_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.isValidManagedKey
import java.time.DateTimeException
import java.time.YearMonth

/** Lenient mapping: an unknown basis/frequency or unparsable month falls back to the default shape. */
internal fun LocalPlannedIncome.toDomain(): PlannedIncome =
    PlannedIncome(
        id = id,
        label = label,
        amountCents = amountCents,
        order = order,
        basis = IncomeBasis.entries.find { it.name == basis } ?: IncomeBasis.FIXED,
        frequency = IncomeFrequency.entries.find { it.name == frequency } ?: IncomeFrequency.MONTHLY,
        monthOfYear = monthOfYear,
        yearMonth = yearMonth?.let(::parseYearMonthOrNull),
        skipMonths = skipMonths.toSet(),
        managedKey = managedKey,
        autoRecord = autoRecord,
        payDay = payDay?.takeIf { it in MIN_DAY_OF_MONTH..MAX_DAY_OF_MONTH },
        payDayEarlierIfClosed = payDayEarlierIfClosed,
        startMonth = startMonth.toYearMonthOrNull(),
        versions = versions.toDomainVersions(),
    )

internal fun PlannedIncome.toLocal(): LocalPlannedIncome =
    LocalPlannedIncome(
        id = id,
        label = label,
        amountCents = amountCents,
        order = order,
        basis = basis.name,
        frequency = frequency.name,
        monthOfYear = monthOfYear,
        yearMonth = yearMonth?.toString(),
        skipMonths = skipMonths.sorted(),
        managedKey = managedKey,
        autoRecord = autoRecord,
        payDay = payDay,
        payDayEarlierIfClosed = payDayEarlierIfClosed,
        startMonth = startMonth?.toString(),
        versions = versions.toLocalVersions(),
    )

internal fun parseYearMonthOrNull(raw: String): YearMonth? =
    try {
        YearMonth.parse(raw)
    } catch (_: DateTimeException) {
        null
    }

/** Mirror of the firestore.rules check; throws [IllegalArgumentException] for a violation. */
internal fun validatePlannedIncome(line: LocalPlannedIncome) {
    require(line.id.isNotBlank()) { "Planned income id is blank" }
    require(line.label.isNotBlank() && line.label.codePointLength() <= MAX_PLAN_LABEL_LENGTH) {
        "Planned income label must be 1..$MAX_PLAN_LABEL_LENGTH characters"
    }
    require(line.amountCents in 1..MAX_AMOUNT_CENTS) { "Planned income amount must be 1..$MAX_AMOUNT_CENTS cents" }
    require(IncomeBasis.entries.any { it.name == line.basis }) { "Invalid income basis ${line.basis}" }
    val frequency = IncomeFrequency.entries.find { it.name == line.frequency }
    requireNotNull(frequency) { "Invalid income frequency ${line.frequency}" }
    val months = MIN_MONTH_OF_YEAR..MAX_MONTH_OF_YEAR
    require(line.skipMonths.size <= MAX_MONTH_OF_YEAR && line.skipMonths.all { it in months }) {
        "Skipped months must be at most 12 values of 1..12"
    }
    validatePlanTiming(line.startMonth, line.versions)
    require(line.payDay == null || line.payDay in MIN_DAY_OF_MONTH..MAX_DAY_OF_MONTH) { "Pay day must be 1..31" }
    require(line.managedKey == null || isValidManagedKey(line.managedKey)) { "Invalid managed key ${line.managedKey}" }
    when (frequency) {
        IncomeFrequency.MONTHLY ->
            require(line.monthOfYear == null && line.yearMonth == null) {
                "A monthly line has no month of year or year-month"
            }
        IncomeFrequency.YEARLY ->
            require(line.monthOfYear in months && line.yearMonth == null) { "A yearly line needs a month 1..12" }
        IncomeFrequency.ONCE ->
            require(line.monthOfYear == null && line.yearMonth?.let(::parseYearMonthOrNull) != null) {
                "A one-off line needs a valid year-month"
            }
    }
}

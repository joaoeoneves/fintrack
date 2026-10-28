package com.joaoeoneves.fintrack.domain.model

/** Builds an extra for tests; defaults to a gross percent extra paid quarterly from January. */
@Suppress("LongParameterList") // mirrors the SalaryExtra constructor
fun testExtra(
    id: String = "flex",
    name: String = "Flexible benefits",
    type: SalaryExtraAmountType = SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM,
    amount: Long = 70_000,
    months: Set<Int> = setOf(1, 4, 7, 10),
    deductions: SalaryExtraDeductions = SalaryExtraDeductions.NONE,
    feePpm: Int = 0,
    amountScope: SalaryExtraAmountScope = SalaryExtraAmountScope.TOTAL,
) = SalaryExtra(id, name, type, amount, months, deductions, feePpm, amountScope)

/** A fixed-cents extra paid once, in [month]. */
fun fixedExtra(
    id: String = "bonus",
    name: String = "Bonus",
    cents: Long = 10_000,
    month: Int = 5,
    deductions: SalaryExtraDeductions = SalaryExtraDeductions.NONE,
) = testExtra(id, name, SalaryExtraAmountType.FIXED_CENTS, cents, setOf(month), deductions)

/** A multiple-of-base extra. */
fun multipleExtra(
    id: String = "profit",
    name: String = "Profit share",
    ppm: Long = 500_000,
    months: Set<Int> = setOf(4),
) = testExtra(id, name, SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM, ppm, months)

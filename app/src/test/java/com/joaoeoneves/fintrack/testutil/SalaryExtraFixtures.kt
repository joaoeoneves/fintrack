package com.joaoeoneves.fintrack.testutil

import com.joaoeoneves.fintrack.domain.model.SalaryExtra
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryExtraDeductions

/** A gross percent extra paid quarterly from January by default (the old "flexible benefits"). */
fun flexExtra(
    id: String = "flex",
    name: String = "Flexible benefits",
    ppm: Long = 70_000,
    months: Set<Int> = setOf(1, 4, 7, 10),
    feePpm: Int = 0,
) = SalaryExtra(id, name, SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM, ppm, months, feePpm = feePpm)

/** A fixed-cents extra paid once, in [month]. */
fun bonusExtra(
    id: String = "bonus",
    name: String = "Bonus",
    cents: Long = 10_000,
    month: Int = 5,
    deductions: SalaryExtraDeductions = SalaryExtraDeductions.NONE,
) = SalaryExtra(id, name, SalaryExtraAmountType.FIXED_CENTS, cents, setOf(month), deductions)

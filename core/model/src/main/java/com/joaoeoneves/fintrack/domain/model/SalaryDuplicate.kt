package com.joaoeoneves.fintrack.domain.model

private val SALARY_WORDS = listOf("salario", "ordenado", "vencimento", "salary", "wage", "paycheck")
private const val SALARY_TOLERANCE_PERCENT = 10L
private const val PERCENT = 100L

/**
 * Whether this manual monthly line looks like a duplicate of the salary the profile already generates: a managed
 * net-salary line exists among [lines] and the label names a salary or the amount is within 10% of it.
 */
fun PlannedIncome.looksLikeSalary(lines: List<PlannedIncome>): Boolean {
    val salary = lines.firstOrNull { it.managedKey == MANAGED_NET_SALARY }
    if (isManaged || frequency != IncomeFrequency.MONTHLY || salary == null) return false
    val normalized = normalizedMerchantName(label)
    val tolerance = SALARY_TOLERANCE_PERCENT * salary.amountCents
    val near = salary.amountCents > 0L && (amountCents - salary.amountCents) * PERCENT in -tolerance..tolerance
    return near || SALARY_WORDS.any { it in normalized }
}

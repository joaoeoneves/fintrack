package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.repository.SalaryLineLabelsProvider

/** [SalaryLineLabelsProvider] for tests: [current] in [language], defaults of both English and pt-PT. */
class FakeSalaryLineLabelsProvider(
    var language: SalaryLineLabels = ENGLISH,
) : SalaryLineLabelsProvider {
    override fun current(): SalaryLineLabels = language

    override fun knownDefaults(): List<SalaryLineLabels> = listOf(ENGLISH, PORTUGUESE)

    companion object {
        val ENGLISH = SalaryLineLabels()
        val PORTUGUESE =
            SalaryLineLabels(
                netSalary = "Salário líquido",
                duodecimos = "Subsídios (duodécimos)",
                meal = "Subsídio de refeição",
                subsidy = "Subsídio",
            )
    }
}

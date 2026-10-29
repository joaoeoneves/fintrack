package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.repository.SalaryLineLabelsProvider

/** [SalaryLineLabelsProvider] for tests: [current] in [language], defaults of both English and pt-PT plus the legacy shared subsidy label. */
class FakeSalaryLineLabelsProvider(
    var language: SalaryLineLabels = ENGLISH,
) : SalaryLineLabelsProvider {
    override fun current(): SalaryLineLabels = language

    override fun knownDefaults(): List<SalaryLineLabels> = listOf(ENGLISH, PORTUGUESE, LEGACY_ENGLISH, LEGACY_PORTUGUESE)

    companion object {
        val ENGLISH = SalaryLineLabels()
        val PORTUGUESE =
            SalaryLineLabels(
                netSalary = "Salário líquido",
                duodecimos = "Subsídios (duodécimos)",
                meal = "Subsídio de refeição",
                holidaySubsidy = "Subsídio de férias",
                christmasSubsidy = "Subsídio de Natal",
            )

        // The single label both subsidy lines shared before they were told apart.
        val LEGACY_ENGLISH = SalaryLineLabels(holidaySubsidy = "Subsidy", christmasSubsidy = "Subsidy")
        val LEGACY_PORTUGUESE = SalaryLineLabels(holidaySubsidy = "Subsídio", christmasSubsidy = "Subsídio")
    }
}

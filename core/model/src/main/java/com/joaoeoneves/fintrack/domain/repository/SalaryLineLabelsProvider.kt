package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels

/** The names of the generated salary lines (and the incomes recorded from them) in the app's language. */
interface SalaryLineLabelsProvider {
    /** The labels in the app's current language: what newly written lines and incomes are named. */
    fun current(): SalaryLineLabels

    /** The default labels of every supported language: a stored label equal to one of them was never renamed. */
    fun knownDefaults(): List<SalaryLineLabels>
}

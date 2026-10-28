package com.joaoeoneves.fintrack.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.repository.SalaryLineLabelsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val supportedLocales = listOf(Locale.forLanguageTag("en"), Locale.forLanguageTag("pt-PT"))

/** [SalaryLineLabelsProvider] resolving the salary line names from the string resources. */
@Singleton
class AppSalaryLineLabels
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SalaryLineLabelsProvider {
        // The in-app language can change at runtime, so the current labels are resolved on every call.
        override fun current(): SalaryLineLabels {
            val locale = AppCompatDelegate.getApplicationLocales().get(0)
            return labelsIn(if (locale == null) context else localized(locale))
        }

        // The defaults ever written are those of each supported language plus the model's own (English).
        override fun knownDefaults(): List<SalaryLineLabels> {
            val perLocale = supportedLocales.map { labelsIn(localized(it)) }
            return perLocale + SalaryLineLabels()
        }

        // False positive: the locale only builds a derived context to resolve names; it never changes the app's own.
        @SuppressLint("AppBundleLocaleChanges")
        private fun localized(locale: Locale): Context =
            context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { setLocale(locale) },
            )

        private fun labelsIn(localized: Context) =
            SalaryLineLabels(
                netSalary = localized.getString(R.string.salary_line_net),
                duodecimos = localized.getString(R.string.salary_line_duodecimos),
                meal = localized.getString(R.string.salary_group_meal),
                subsidy = localized.getString(R.string.salary_line_subsidy),
            )
    }

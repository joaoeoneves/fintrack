package com.joaoeoneves.fintrack.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.LegacyExtraNames
import com.joaoeoneves.fintrack.domain.repository.LegacyExtraNamesProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [LegacyExtraNamesProvider] resolving the default extra names in the app's current language. */
@Singleton
class AppLegacyExtraNames
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : LegacyExtraNamesProvider {
        // False positive: the locale only builds a derived context to resolve names; it never changes the app's own.
        @SuppressLint("AppBundleLocaleChanges")
        override fun names(): LegacyExtraNames {
            val locale = AppCompatDelegate.getApplicationLocales().get(0)
            val localized =
                if (locale == null) {
                    context
                } else {
                    context.createConfigurationContext(
                        Configuration(context.resources.configuration).apply { setLocale(locale) },
                    )
                }
            return LegacyExtraNames(
                flex = localized.getString(R.string.salary_extra_default_flex),
                profitShare = localized.getString(R.string.salary_extra_default_profit_share),
                bonus = localized.getString(R.string.salary_extra_default_bonus),
                deletedCategory = localized.getString(R.string.category_deleted_placeholder),
            )
        }
    }

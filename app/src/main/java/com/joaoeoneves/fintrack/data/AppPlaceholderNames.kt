package com.joaoeoneves.fintrack.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.repository.PlaceholderNamesProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [PlaceholderNamesProvider] resolving the names in the app's current language. */
@Singleton
class AppPlaceholderNames
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PlaceholderNamesProvider {
        // False positive: the locale only builds a derived context to resolve names; it never changes the app's own.
        @SuppressLint("AppBundleLocaleChanges")
        override fun deletedCategory(): String {
            val locale = AppCompatDelegate.getApplicationLocales().get(0)
            val localized =
                if (locale == null) {
                    context
                } else {
                    context.createConfigurationContext(
                        Configuration(context.resources.configuration).apply { setLocale(locale) },
                    )
                }
            return localized.getString(R.string.category_deleted_placeholder)
        }
    }

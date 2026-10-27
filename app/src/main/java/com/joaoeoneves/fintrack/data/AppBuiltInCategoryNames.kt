package com.joaoeoneves.fintrack.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.repository.BuiltInCategoryNames
import com.joaoeoneves.fintrack.ui.common.builtInNameRes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val supportedLocales = listOf(Locale.forLanguageTag("en"), Locale.forLanguageTag("pt-PT"))

/** [BuiltInCategoryNames] resolving the built-in name string resources in each supported locale. */
@Singleton
class AppBuiltInCategoryNames
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BuiltInCategoryNames {
        private val names: Map<CategoryId, Set<String>> by lazy { resolveNames() }

        // False positive: no dynamic features / Play asset delivery, and the locale only builds a
        // derived context to resolve names; it never changes the app's own locale.
        @SuppressLint("AppBundleLocaleChanges")
        private fun resolveNames(): Map<CategoryId, Set<String>> {
            val localized =
                supportedLocales.map { locale ->
                    context.createConfigurationContext(
                        Configuration(context.resources.configuration).apply { setLocale(locale) },
                    )
                }
            return builtInNameRes.mapValues { (_, res) -> localized.mapTo(mutableSetOf()) { it.getString(res) } }
        }

        override fun localizedNames(): Map<CategoryId, Set<String>> = names
    }

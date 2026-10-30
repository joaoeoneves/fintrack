package com.joaoeoneves.fintrack.data

import android.content.Context
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.StarterCategories
import com.joaoeoneves.fintrack.domain.model.StarterCategoryNames
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [StarterCategoryNames] resolved from string resources in the current language. */
@Singleton
class AppStarterCategoryNames
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : StarterCategoryNames {
        override fun names(): Map<String, String> =
            mapOf(
                StarterCategories.HOME_ID to context.getString(R.string.starter_category_home),
                StarterCategories.TRANSPORT_ID to context.getString(R.string.starter_category_transport),
                StarterCategories.HEALTH_ID to context.getString(R.string.starter_category_health),
                StarterCategories.LEISURE_ID to context.getString(R.string.starter_category_leisure),
            )
    }

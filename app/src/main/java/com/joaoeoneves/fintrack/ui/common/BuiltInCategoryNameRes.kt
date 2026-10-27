package com.joaoeoneves.fintrack.ui.common

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId

/** Localized default name string resource of each built-in category (plain, no Compose). */
val builtInNameRes: Map<CategoryId, Int> =
    mapOf(
        BuiltInCategories.TRANSFER.id to R.string.category_transfer,
        BuiltInCategories.INVESTMENTS.id to R.string.category_investments,
        BuiltInCategories.SHOPPING.id to R.string.category_shopping,
        BuiltInCategories.FOOD.id to R.string.category_food,
        BuiltInCategories.RECURRING.id to R.string.category_recurring,
    )

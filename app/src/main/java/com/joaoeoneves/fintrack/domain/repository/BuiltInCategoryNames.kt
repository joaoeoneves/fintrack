package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.CategoryId

/** Localized default names of the built-in categories across every supported app language. */
interface BuiltInCategoryNames {
    /** All localized default names per built-in category id. */
    fun localizedNames(): Map<CategoryId, Set<String>>
}

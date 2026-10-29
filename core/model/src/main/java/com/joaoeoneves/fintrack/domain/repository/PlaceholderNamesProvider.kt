package com.joaoeoneves.fintrack.domain.repository

/** The names written into stored data for records that lost what they pointed at, in the app language. */
interface PlaceholderNamesProvider {
    /** The name of the placeholder category kept by a restore or migration for a category that no longer exists. */
    fun deletedCategory(): String
}

package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.repository.PlaceholderNamesProvider

/** [PlaceholderNamesProvider] for tests, returning [deleted] as the deleted-category placeholder name. */
class FakePlaceholderNames(
    private val deleted: String = "Deleted category",
) : PlaceholderNamesProvider {
    override fun deletedCategory(): String = deleted
}

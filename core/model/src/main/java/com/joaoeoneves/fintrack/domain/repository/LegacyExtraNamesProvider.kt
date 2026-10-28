package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.LegacyExtraNames

/** The names given to the extras migrated from the legacy flex / profit-share / bonus fields, in the app language. */
interface LegacyExtraNamesProvider {
    fun names(): LegacyExtraNames
}

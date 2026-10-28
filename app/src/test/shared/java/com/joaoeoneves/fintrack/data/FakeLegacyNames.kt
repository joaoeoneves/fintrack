package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.LegacyExtraNames
import com.joaoeoneves.fintrack.domain.repository.LegacyExtraNamesProvider

/** [LegacyExtraNamesProvider] for tests, returning [names] (the model's English defaults unless given). */
class FakeLegacyNames(
    private val names: LegacyExtraNames = LegacyExtraNames(),
) : LegacyExtraNamesProvider {
    override fun names(): LegacyExtraNames = names
}

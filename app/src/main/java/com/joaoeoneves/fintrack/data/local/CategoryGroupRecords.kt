package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH

internal fun LocalCategoryGroup.toDomain(): Envelope = Envelope(id = id, name = name, order = order)

internal fun Envelope.toLocal(): LocalCategoryGroup = LocalCategoryGroup(id = id, name = name, order = order)

/** Mirror of the firestore.rules check; throws [IllegalArgumentException] for a violation. */
internal fun validateCategoryGroup(group: LocalCategoryGroup) {
    requireIdShape(group.id)
    requireText(group.name, MAX_GROUP_NAME_LENGTH, "Envelope name")
}

package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH

internal fun LocalCategoryGroup.toDomain(): CategoryGroup = CategoryGroup(id = id, name = name, order = order)

internal fun CategoryGroup.toLocal(): LocalCategoryGroup = LocalCategoryGroup(id = id, name = name, order = order)

/** Mirror of the firestore.rules check; throws [IllegalArgumentException] for a violation. */
internal fun validateCategoryGroup(group: LocalCategoryGroup) {
    requireCategoryIdShape(group.id)
    requireText(group.name, MAX_GROUP_NAME_LENGTH, "Group name")
}

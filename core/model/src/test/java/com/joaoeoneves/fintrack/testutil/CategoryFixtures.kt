package com.joaoeoneves.fintrack.testutil

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind

/** A user-defined (non-built-in) category for tests. */
fun userCategory(
    id: String,
    name: String = id,
    order: Int = 10,
    kind: CategoryKind = CategoryKind.SPENDING,
    archived: Boolean = false,
) = Category(
    id = CategoryId(id),
    customName = name,
    iconKey = "home",
    colorKey = "teal",
    kind = kind,
    order = order,
    archived = archived,
    builtIn = false,
)

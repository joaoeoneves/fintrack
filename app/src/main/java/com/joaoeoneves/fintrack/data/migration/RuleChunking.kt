package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId

/** Most writes put into one Firestore batch (the hard limit is 500). */
internal const val BATCH_CHUNK_SIZE = 400

// Each write whose category isn't built in makes firestore.rules look up that category document
// (existsAfter). Rules may make at most 10 document-access calls per single-document request and 20
// per batched write/transaction; repeated calls to the same path are cached and count once. A chunk
// therefore references at most this many distinct custom categories, which stays safely under the
// 20-call batch limit.
internal const val MAX_CUSTOM_CATEGORIES_PER_BATCH = 15

/**
 * Splits these items into write batches of at most [maxSize] items that reference at most
 * [maxCustomCategories] distinct non-built-in categories. [categoryOf] returns the category an item
 * references, or `null` when its write needs no category lookup. Order is preserved.
 */
internal fun <T> List<T>.chunkedForRules(
    maxSize: Int = BATCH_CHUNK_SIZE,
    maxCustomCategories: Int = MAX_CUSTOM_CATEGORIES_PER_BATCH,
    categoryOf: (T) -> CategoryId?,
): List<List<T>> {
    val chunks = mutableListOf<List<T>>()
    var current = mutableListOf<T>()
    val customIds = mutableSetOf<CategoryId>()
    forEach { item ->
        val id = categoryOf(item)?.takeUnless { BuiltInCategories.isBuiltIn(it) }
        val exceedsCustomLimit = id != null && id !in customIds && customIds.size >= maxCustomCategories
        if (current.size >= maxSize || exceedsCustomLimit) {
            chunks += current
            current = mutableListOf()
            customIds.clear()
        }
        current += item
        if (id != null) customIds += id
    }
    if (current.isNotEmpty()) chunks += current
    return chunks
}

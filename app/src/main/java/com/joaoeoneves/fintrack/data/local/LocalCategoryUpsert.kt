package com.joaoeoneves.fintrack.data.local

/** This document with every one of [records] validated and upserted (all or nothing). */
internal fun LocalDataDocument.withCategories(records: List<LocalCategory>): LocalDataDocument {
    records.forEach(::validateCategory)
    val merged = records.fold(categories) { acc, record -> acc.upserted(record) { it.id == record.id } }
    return copy(categories = merged)
}

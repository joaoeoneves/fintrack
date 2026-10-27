package com.joaoeoneves.fintrack.data.local

/** This list with [item] replacing the first element matching [sameAs], or appended if none does. */
internal inline fun <T> List<T>.upserted(
    item: T,
    sameAs: (T) -> Boolean,
): List<T> {
    val index = indexOfFirst(sameAs)
    return if (index < 0) this + item else toMutableList().also { it[index] = item }
}

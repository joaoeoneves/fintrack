package com.joaoeoneves.fintrack.domain.model

/**
 * Whether [name] (trimmed, case-insensitive) is already used by an envelope other than [excludingId], or is one of
 * the [reserved] names (such as the label of the plan's implicit ungrouped row).
 */
fun isGroupNameTaken(
    name: String,
    groups: List<Envelope>,
    excludingId: String? = null,
    reserved: Collection<String> = emptyList(),
): Boolean {
    val wanted = name.trim()
    return groups.any { it.id != excludingId && it.name.trim().equals(wanted, ignoreCase = true) } ||
        reserved.any { it.trim().equals(wanted, ignoreCase = true) }
}

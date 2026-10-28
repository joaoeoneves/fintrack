package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalAutomation
import com.joaoeoneves.fintrack.data.local.validOrNull

/** The guest's automation settings when valid and the account has none, keeping the cursors of copied lines only. */
internal fun automationToCopy(
    local: LocalAutomation?,
    remote: RemoteAccountSnapshot,
    copiedLineIds: Set<String>,
): LocalAutomation? =
    local
        ?.takeUnless { remote.hasAutomation }
        ?.validOrNull()
        ?.let { it.copy(lineThrough = it.lineThrough.filterKeys { id -> id in copiedLineIds }) }

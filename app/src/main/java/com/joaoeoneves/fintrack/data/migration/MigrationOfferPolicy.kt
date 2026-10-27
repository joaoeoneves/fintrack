package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.domain.model.Session

/**
 * Whether to offer copying the local guest data into the account: only while signed in, when the
 * local store holds data, and when the user has not already decided about this revision of it.
 */
fun shouldOfferMigration(
    session: Session,
    local: LocalDataDocument,
    handledRevision: Long?,
): Boolean = session is Session.SignedIn && !local.isEmpty && local.revision != handledRevision

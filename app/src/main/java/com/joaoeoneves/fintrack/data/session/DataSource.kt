package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

/** Where data is read from and written to for the current session. */
sealed interface DataSource {
    data object Local : DataSource

    /** Firestore; [uid] is null when signed out (the Firestore repos then return empty / fail). */
    data class Remote(
        val uid: String?,
    ) : DataSource
}

/** The data source per resolved session; [Session.Loading] is skipped so consumers wait for it. */
fun Flow<Session>.toDataSource(): Flow<DataSource> =
    mapNotNull { session ->
        when (session) {
            Session.Loading -> null
            Session.Guest -> DataSource.Local
            Session.SignedOut -> DataSource.Remote(null)
            is Session.SignedIn -> DataSource.Remote(session.user.uid)
        }
    }.distinctUntilChanged()

internal fun SessionRepository.dataSource(): Flow<DataSource> = session.toDataSource()

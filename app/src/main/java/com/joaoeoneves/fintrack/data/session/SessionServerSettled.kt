package com.joaoeoneves.fintrack.data.session

import com.google.firebase.firestore.FirebaseFirestore
import com.joaoeoneves.fintrack.domain.repository.ServerSettled
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** [ServerSettled] for the signed-in account: waits until Firestore has sent every pending write to the server. */
class FirestoreServerSettled
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
    ) : ServerSettled {
        override suspend fun awaitPendingWrites() {
            firestore.waitForPendingWrites().await()
        }
    }

/** [ServerSettled] for a guest: there is no server, so everything is settled at once. */
class LocalServerSettled
    @Inject
    constructor() : ServerSettled {
        override suspend fun awaitPendingWrites() = Unit
    }

/** [ServerSettled] that waits on Firestore when signed in and returns at once as a guest. */
@Singleton
class SessionServerSettled
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        remote: Lazy<FirestoreServerSettled>,
        local: LocalServerSettled,
    ) : ServerSettled {
        private val switch = SessionSwitch<ServerSettled>(sessionRepository, remote, local)

        override suspend fun awaitPendingWrites() = switch.call { it.awaitPendingWrites() }
    }

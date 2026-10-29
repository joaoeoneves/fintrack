package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestoreEnvelopeRepository
import com.joaoeoneves.fintrack.data.local.LocalEnvelopeRepository
import com.joaoeoneves.fintrack.di.ApplicationScope
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import com.joaoeoneves.fintrack.domain.repository.EnvelopeRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** [EnvelopeRepository] that talks to Firestore when signed in and to the local store as a guest. */
@Singleton
class SessionEnvelopeRepository
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        @ApplicationScope scope: CoroutineScope,
        remote: Lazy<FirestoreEnvelopeRepository>,
        local: LocalEnvelopeRepository,
    ) : EnvelopeRepository {
        private val switch = SessionSwitch<EnvelopeRepository>(sessionRepository, remote, local)

        private val observeEnvelopesShared = switch.shared(scope, emptyList()) { it.observeEnvelopes() }

        override fun observeEnvelopes(): Flow<List<Envelope>> = observeEnvelopesShared

        override suspend fun saveEnvelope(edit: EnvelopeEdit): Result<String> = switch.call { it.saveEnvelope(edit) }

        override suspend fun reorderEnvelopes(ids: List<String>) = switch.call { it.reorderEnvelopes(ids) }

        override suspend fun deleteEnvelope(id: String): Result<Unit> = switch.call { it.deleteEnvelope(id) }
    }

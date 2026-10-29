package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.data.FirestorePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.local.LocalPlannedIncomeRepository
import com.joaoeoneves.fintrack.data.local.LocalStoreTestBase
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SessionPlannedIncomeRepository] as a guest: local only, the Firestore Lazy must never be resolved. */
class SessionPlannedIncomeRepositoryTest : LocalStoreTestBase() {
    private val sessions = FakeSessionRepository(initialSession = Session.Guest)
    private val remote = CountingLazy<FirestorePlannedIncomeRepository>(null)
    private val repo by lazy {
        SessionPlannedIncomeRepository(
            sessions,
            CoroutineScope(Dispatchers.Unconfined),
            remote,
            LocalPlannedIncomeRepository(newStore(), SequenceIds("g")),
        )
    }
    private val line = PlannedIncome("", "Salary", 100_000L, 0)

    @Test
    fun guest_observeEmitsLocalLines_withoutResolvingRemote() =
        runStoreTest {
            assertEquals(emptyList<PlannedIncome>(), repo.observePlannedIncome().first())
            assertEquals(0, remote.gets)
        }

    @Test
    fun guest_saveAndDelete_roundTripLocally() =
        runStoreTest {
            val id = repo.savePlannedIncome(line).getOrThrow()

            assertEquals(listOf(line.copy(id = id)), repo.observePlannedIncome().first())
            assertTrue(repo.deletePlannedIncome(id).isSuccess)
            assertEquals(emptyList<PlannedIncome>(), repo.observePlannedIncome().first())
            assertEquals(0, remote.gets)
        }

    @Test
    fun guest_invalidLine_failsThroughTheLocalValidation() =
        runStoreTest {
            assertTrue(repo.savePlannedIncome(line.copy(amountCents = 0)).isFailure)
        }
}

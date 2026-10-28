package com.joaoeoneves.fintrack.ui.plan.envelopes

import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnvelopeUndoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val a = CategoryId("a")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun deleted(position: Int) =
        DeletedEnvelope(
            EnvelopeEdit("gone", "Gone", null, "l1", setOf(a), mapOf(a to Budget(a, 100))),
            position,
        )

    private fun order(repo: FakeEnvelopeRepository) = repo.envelopes.sortedBy { it.order }.map { it.id }

    @Test
    fun restore_resavesAndMovesBackToPosition() =
        runTest(dispatcher) {
            val categories = FakeCategoryRepository(listOf(userCategory("a")))
            val budgets = FakeBudgetRepository()
            val repo =
                FakeEnvelopeRepository(listOf(Envelope("x", "X", 0), Envelope("y", "Y", 1)), categories, budgets)
            val vm = EnvelopeUndoViewModel(repo)

            vm.restore(deleted(1))
            advanceUntilIdle()

            val edit = repo.savedEdits.single()
            assertEquals("gone", edit.id)
            assertEquals(mapOf(a to Budget(a, 100)), edit.limits)
            assertEquals(listOf("x", "gone", "y"), order(repo))
        }

    @Test
    fun restore_positionBeyondEnd_isClamped() =
        runTest(dispatcher) {
            val repo = FakeEnvelopeRepository(listOf(Envelope("x", "X", 0)))
            EnvelopeUndoViewModel(repo).apply { restore(deleted(99)) }
            advanceUntilIdle()
            assertEquals(listOf("x", "gone"), order(repo))
        }

    @Test
    fun restore_positionZero_goesFirst() =
        runTest(dispatcher) {
            val repo = FakeEnvelopeRepository(listOf(Envelope("x", "X", 0)))
            EnvelopeUndoViewModel(repo).restore(deleted(0))
            advanceUntilIdle()
            assertEquals(listOf("gone", "x"), order(repo))
        }

    @Test
    fun restore_saveFailure_doesNotReorder() =
        runTest(dispatcher) {
            val repo =
                FakeEnvelopeRepository(listOf(Envelope("x", "X", 0)), nextWriteError = IllegalStateException("x"))
            EnvelopeUndoViewModel(repo).restore(deleted(0))
            advanceUntilIdle()
            assertEquals(listOf("x"), order(repo))
        }
}

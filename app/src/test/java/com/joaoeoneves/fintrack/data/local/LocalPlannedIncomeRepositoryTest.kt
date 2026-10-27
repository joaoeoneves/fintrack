package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlannedIncomeRepositoryTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalPlannedIncomeRepository(store, SequenceIds("p")) }

    private fun line(
        id: String = "",
        label: String = "Salary",
        amount: Long = 100_000L,
        order: Int = 0,
    ) = PlannedIncome(id, label, amount, order)

    private suspend fun lines() = repo.observePlannedIncome().first()

    @Test
    fun observe_empty() =
        runStoreTest {
            assertEquals(emptyList<PlannedIncome>(), lines())
        }

    @Test
    fun save_blankId_generatesOne_andReturnsIt() =
        runStoreTest {
            val id = repo.savePlannedIncome(line()).getOrThrow()

            assertEquals("p1", id)
            assertEquals(listOf(line(id = "p1")), lines())
        }

    @Test
    fun save_existingId_upsertsInPlace_keepingTheId() =
        runStoreTest {
            val id = repo.savePlannedIncome(line()).getOrThrow()

            assertEquals(id, repo.savePlannedIncome(line(id = id, label = "Pay", amount = 5)).getOrThrow())

            assertEquals(listOf(line(id = id, label = "Pay", amount = 5)), lines())
        }

    @Test
    fun delete_removesLine_missingIsNoOpSuccess() =
        runStoreTest {
            val id = repo.savePlannedIncome(line()).getOrThrow()

            assertTrue(repo.deletePlannedIncome("nope").isSuccess)
            assertEquals(1, lines().size)
            assertTrue(repo.deletePlannedIncome(id).isSuccess)
            assertEquals(emptyList<PlannedIncome>(), lines())
        }

    @Test
    fun delete_missing_doesNotBumpRevision() =
        runStoreTest {
            repo.savePlannedIncome(line())
            repo.deletePlannedIncome("nope")

            assertEquals(1L, store.data.first().revision)
        }

    @Test
    fun save_blankOrWhitespaceLabel_failsNothingWritten() =
        runStoreTest {
            assertTrue(repo.savePlannedIncome(line(label = "")).exceptionOrNull() is IllegalArgumentException)
            assertTrue(repo.savePlannedIncome(line(label = "   ")).isFailure)
            assertFalse(dataFile.exists())
        }

    @Test
    fun save_label50CodePoints_accepted_51Rejected() =
        runStoreTest {
            assertTrue(repo.savePlannedIncome(line(label = "a".repeat(50))).isSuccess)
            assertTrue(repo.savePlannedIncome(line(label = "a".repeat(51))).isFailure)
            assertEquals(1, lines().size)
        }

    @Test
    fun save_emojiCountAsOneCodePoint() =
        runStoreTest {
            val fifty = "💰".repeat(50)

            assertTrue(repo.savePlannedIncome(line(label = fifty)).isSuccess)
            assertTrue(repo.savePlannedIncome(line(label = fifty + "💰")).isFailure)
        }

    @Test
    fun save_zeroOrNegativeAmount_fails_oneCentAccepted() =
        runStoreTest {
            assertTrue(repo.savePlannedIncome(line(amount = 0)).exceptionOrNull() is IllegalArgumentException)
            assertTrue(repo.savePlannedIncome(line(amount = -5)).isFailure)
            assertTrue(repo.savePlannedIncome(line(amount = 1)).isSuccess)
        }

    @Test
    fun observe_sortsByOrderThenId() =
        runStoreTest {
            repo.savePlannedIncome(line(id = "b", order = 1))
            repo.savePlannedIncome(line(id = "z", order = 0))
            repo.savePlannedIncome(line(id = "a", order = 1))

            assertEquals(listOf("z", "a", "b"), lines().map { it.id })
        }

    @Test
    fun lines_persistAcrossInstances() =
        runStoreTest {
            repo.savePlannedIncome(line(id = "k"))

            val reloaded = LocalPlannedIncomeRepository(newStore(), SequenceIds()).observePlannedIncome().first()
            assertEquals(listOf(line(id = "k")), reloaded)
        }
}

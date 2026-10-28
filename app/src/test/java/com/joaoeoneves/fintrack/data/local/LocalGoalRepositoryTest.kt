package com.joaoeoneves.fintrack.data.local

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalGoalRepositoryTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalGoalRepository(store, SequenceIds("g")) }

    private fun goal(
        id: String = "",
        name: String = "Holiday",
        target: Long = 120_000L,
        order: Int = 0,
        targetDate: LocalDate? = null,
    ) = SavingsGoal(
        id = id,
        name = name,
        targetCents = target,
        targetType = GoalTargetType.ANNUAL,
        startDate = LocalDate.of(2026, 1, 1),
        targetDate = targetDate,
        order = order,
    )

    private suspend fun goals() = repo.observeGoals().first()

    @Test
    fun observe_empty() =
        runStoreTest {
            assertEquals(emptyList<SavingsGoal>(), goals())
        }

    @Test
    fun save_blankId_generatesOne_andReturnsIt() =
        runStoreTest {
            val id = repo.saveGoal(goal()).getOrThrow()

            assertEquals("g1", id)
            assertEquals(listOf(goal(id = "g1")), goals())
        }

    @Test
    fun save_existingId_upsertsInPlace() =
        runStoreTest {
            val id = repo.saveGoal(goal()).getOrThrow()

            assertEquals(id, repo.saveGoal(goal(id = id, name = "Car", target = 5)).getOrThrow())

            assertEquals(listOf(goal(id = id, name = "Car", target = 5)), goals())
        }

    @Test
    fun delete_removesGoal_missingIsNoOpSuccess() =
        runStoreTest {
            val id = repo.saveGoal(goal()).getOrThrow()

            assertTrue(repo.deleteGoal("nope").isSuccess)
            assertEquals(1, goals().size)
            assertTrue(repo.deleteGoal(id).isSuccess)
            assertEquals(emptyList<SavingsGoal>(), goals())
        }

    @Test
    fun save_invalid_failsNothingWritten() =
        runStoreTest {
            assertTrue(repo.saveGoal(goal(name = "")).exceptionOrNull() is IllegalArgumentException)
            assertTrue(repo.saveGoal(goal(name = "a".repeat(51))).isFailure)
            assertTrue(repo.saveGoal(goal(target = -1)).isFailure)
            assertTrue(repo.saveGoal(goal(targetDate = LocalDate.of(2025, 12, 31))).isFailure)
            assertFalse(dataFile.exists())
        }

    @Test
    fun save_noTargetPlan_withRecurringId_roundTrips() =
        runStoreTest {
            val plan = goal(id = "p1", target = 0).copy(recurringId = "r1")

            assertTrue(repo.saveGoal(plan).isSuccess)

            assertEquals(listOf(plan), goals())
        }

    @Test
    fun observe_sortsByOrderThenId() =
        runStoreTest {
            repo.saveGoal(goal(id = "b", order = 1))
            repo.saveGoal(goal(id = "z", order = 0))
            repo.saveGoal(goal(id = "a", order = 1))

            assertEquals(listOf("z", "a", "b"), goals().map { it.id })
        }
}

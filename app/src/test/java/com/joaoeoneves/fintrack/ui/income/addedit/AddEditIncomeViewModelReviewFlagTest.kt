package com.joaoeoneves.fintrack.ui.income.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.testutil.deleteTransactionFor
import com.joaoeoneves.fintrack.testutil.testIncome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant

/** Saving the income form counts as reviewing the record. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditIncomeViewModelReviewFlagTest {
    private val testDispatcher = StandardTestDispatcher()
    private val flagged =
        testIncome("i1", 1_250L, Instant.parse("2026-10-10T10:00:00Z")).copy(needsReview = true)

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repo: FakeIncomeRepository,
        id: String?,
    ) = AddEditIncomeViewModel(
        repo,
        deleteTransactionFor(incomes = repo),
        SavedStateHandle(if (id == null) emptyMap() else mapOf("incomeId" to id)),
        Clock.systemDefaultZone(),
    )

    @Test
    fun `saving an edited flagged income clears the review flag`() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository(listOf(flagged))
            val vm = viewModel(repo, "i1")
            advanceUntilIdle()

            vm.onSave()
            advanceUntilIdle()

            assertFalse(repo.getIncome("i1").getOrThrow()!!.needsReview)
        }

    @Test
    fun `saving a new income stores it as reviewed`() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            val vm = viewModel(repo, null)
            advanceUntilIdle()
            vm.onSourceChanged("Gift")
            vm.onAmountChanged(5_000L)

            vm.onSave()
            advanceUntilIdle()

            val all = repo.getAllIncome().getOrThrow()
            assertFalse(all.single().needsReview)
        }
}

package com.joaoeoneves.fintrack.ui.insights

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.SpendingSplit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The fixed/variable [InsightsUiState.Content.split] and the per-envelope breakdown of [InsightsViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class InsightsViewModelSplitTest : InsightsViewModelTestBase() {
    @Test
    fun emptyRepositories_hasZeroSplit_andNoEnvelopes() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            val content = vm.content()
            assertEquals(SpendingSplit(0L, 0L, 0L), content.split)
            assertFalse(content.breakdown.hasEnvelopes)
        }

    @Test
    fun split_separatesRecurringFromManual_andSaving() =
        runTest(testDispatcher) {
            val repo =
                FakeExpenseRepository(
                    listOf(
                        expense("rec_a_2026-10", 1_200L),
                        expense("manual", 300L),
                        refund("manualRefund", 100L),
                        expense("rec_inv", 500L, BuiltInCategories.INVESTMENTS),
                        expense("old", 9_999L, date = noonOn(previousMonth, 28)),
                    ),
                )
            val vm = viewModel(repo)
            collect(vm)

            assertEquals(SpendingSplit(1_200L, 200L, 500L), vm.content().split)
            assertEquals(1_400L, vm.content().split.spendingCents)
        }

    @Test
    fun breakdownEnvelopes_followCategoryAssignment() =
        runTest(testDispatcher) {
            val categories = FakeCategoryRepository()
            val groups = FakeEnvelopeRepository()
            val food = groups.addEnvelope("Food").getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, food)
            val repo =
                FakeExpenseRepository(
                    listOf(
                        expense("f", 800L, BuiltInCategories.FOOD),
                        expense("s", 150L),
                    ),
                )
            val vm = viewModel(repo, categoryRepository = categories, groupRepository = groups)
            collect(vm)

            val result = vm.content().breakdown
            assertTrue(result.hasEnvelopes)
            assertEquals(
                "Food",
                result.envelopes.envelopes
                    .first()
                    .envelope.name,
            )
            assertEquals(
                800L,
                result.envelopes.envelopes
                    .first()
                    .spentCents,
            )
            assertEquals(150L, result.envelopes.unenvelopedSpentCents)
        }
}

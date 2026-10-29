package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.ui.activity.analytics.EnvelopeChip
import com.joaoeoneves.fintrack.ui.activity.analytics.NO_ENVELOPE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * The envelope filter of [ActivityViewModel]: selection, route seeding, stale ids, category scope and the
 * derived envelope chips. FOOD is filed under the "Food" envelope and TRANSFER under "Bills"; SHOPPING is in none.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelEnvelopeTest : ActivityViewModelTestBase() {
    private val categoryRepo = FakeCategoryRepository()
    private val envelopeRepo = FakeEnvelopeRepository()
    private val foodEnvelope = runBlocking { envelopeRepo.addEnvelope("Food").getOrThrow() }
    private val billsEnvelope = runBlocking { envelopeRepo.addEnvelope("Bills").getOrThrow() }

    private val lunch = expense("lunch", 900L, category = BuiltInCategories.FOOD)
    private val rent = expense("rent", 5_000L, category = BuiltInCategories.TRANSFER)
    private val mug = expense("mug", 300L)
    private val salary = income("salary", 250_000L)

    init {
        runBlocking {
            categoryRepo.setGroup(BuiltInCategories.FOOD.id, foodEnvelope)
            categoryRepo.setGroup(BuiltInCategories.TRANSFER.id, billsEnvelope)
        }
    }

    private fun fixture(
        vararg p: Pair<String, Any?>,
        expenses: List<Expense> = listOf(lunch, rent, mug),
    ) = Fixture(expenses, listOf(salary), args(*p), categoryRepo, envelopes = envelopeRepo)

    private fun ActivityUiState.Content.ids() = items.map { it.id }.toSet()

    private fun foodChip(spent: Long) = EnvelopeChip(foodEnvelope, "Food", spent, "violet")

    private fun billsChip(spent: Long) = EnvelopeChip(billsEnvelope, "Bills", spent, "blue")

    private fun noEnvChip(spent: Long) = EnvelopeChip(NO_ENVELOPE, null, spent, "slate")

    @Test
    fun noEnvelope_byDefault_showsEverything() =
        runTest(testDispatcher) {
            val content = content(fixture().vm)

            assertNull(content.filters.envelope)
            assertNull(content.categoryScope)
            assertEquals(setOf("lunch", "rent", "mug", "salary"), content.ids())
        }

    @Test
    fun onEnvelopeSelected_filtersToThatEnvelopesExpenses_andHidesIncome() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)

            vm.onEnvelopeSelected(foodEnvelope)
            advanceUntilIdle()

            assertEquals(foodEnvelope, current(vm).filters.envelope)
            assertEquals(setOf("lunch"), current(vm).ids())
            assertEquals(foodEnvelope, vm.envelopeFilter.value)
        }

    @Test
    fun onEnvelopeSelected_noEnvelope_showsOnlyUnenvelopedExpenses() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)

            vm.onEnvelopeSelected(NO_ENVELOPE)
            advanceUntilIdle()

            assertEquals(NO_ENVELOPE, current(vm).filters.envelope)
            assertEquals(setOf("mug"), current(vm).ids())
        }

    @Test
    fun onEnvelopeSelected_null_clearsTheFilter() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)
            vm.onEnvelopeSelected(foodEnvelope)
            advanceUntilIdle()

            vm.onEnvelopeSelected(null)
            advanceUntilIdle()

            // Selecting an envelope moved to the Expenses tab, which clearing it does not undo.
            assertNull(current(vm).filters.envelope)
            assertEquals(setOf("lunch", "rent", "mug"), current(vm).ids())
        }

    @Test
    fun incomeType_ignoresTheEnvelope() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)
            vm.onEnvelopeSelected(foodEnvelope)
            vm.onTypeSelected(ActivityType.INCOME)
            advanceUntilIdle()

            assertEquals(setOf("salary"), current(vm).ids())
        }

    @Test
    fun onEnvelopeSelected_clearsACategoryOfAnotherEnvelope() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)
            vm.onCategorySelected(BuiltInCategories.TRANSFER.id)
            advanceUntilIdle()

            vm.onEnvelopeSelected(foodEnvelope)
            advanceUntilIdle()

            assertNull(vm.categoryFilter.value)
            assertNull(current(vm).filters.category)
            assertEquals(setOf("lunch"), current(vm).ids())
        }

    @Test
    fun onEnvelopeSelected_keepsACategoryOfThatEnvelope() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)
            vm.onCategorySelected(BuiltInCategories.FOOD.id)
            advanceUntilIdle()

            vm.onEnvelopeSelected(foodEnvelope)
            advanceUntilIdle()

            assertEquals(BuiltInCategories.FOOD.id, vm.categoryFilter.value)
            assertEquals(setOf("lunch"), current(vm).ids())
        }

    @Test
    fun onEnvelopeSelected_noEnvelope_clearsAnEnvelopedCategory_butKeepsAnUnenvelopedOne() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)
            vm.onCategorySelected(BuiltInCategories.SHOPPING.id)
            vm.onEnvelopeSelected(NO_ENVELOPE)
            advanceUntilIdle()
            assertEquals(BuiltInCategories.SHOPPING.id, vm.categoryFilter.value)

            vm.onCategorySelected(BuiltInCategories.FOOD.id)
            vm.onEnvelopeSelected(NO_ENVELOPE)
            advanceUntilIdle()
            assertNull(vm.categoryFilter.value)
        }

    @Test
    fun categoryScope_isTheSelectedEnvelopesCategories() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)

            vm.onEnvelopeSelected(billsEnvelope)
            advanceUntilIdle()
            assertEquals(setOf(BuiltInCategories.TRANSFER.id), current(vm).categoryScope)

            vm.onEnvelopeSelected(NO_ENVELOPE)
            advanceUntilIdle()
            val scope = current(vm).categoryScope.orEmpty()
            assertFalse(BuiltInCategories.FOOD.id in scope)
            assertTrue(BuiltInCategories.SHOPPING.id in scope)
        }

    @Test
    fun routeEnvelope_seedsTheFilter() =
        runTest(testDispatcher) {
            val content = content(fixture("envelope" to foodEnvelope).vm)

            assertEquals(foodEnvelope, content.filters.envelope)
            assertEquals(setOf("lunch"), content.ids())
        }

    @Test
    fun routeEnvelope_emptyToken_meansNoEnvelope() =
        runTest(testDispatcher) {
            val content = content(fixture("envelope" to NO_ENVELOPE).vm)

            assertEquals(NO_ENVELOPE, content.filters.envelope)
            assertEquals(setOf("mug"), content.ids())
        }

    @Test
    fun userChoice_overridesTheRouteEnvelope_andSurvivesRecreation() =
        runTest(testDispatcher) {
            val fixture = fixture("envelope" to foodEnvelope)
            content(fixture.vm)
            fixture.vm.onEnvelopeSelected(billsEnvelope)
            advanceUntilIdle()
            assertEquals("envelope arg untouched", foodEnvelope, fixture.handle.get<String>("envelope"))

            assertEquals(billsEnvelope, content(fixture.recreate()).filters.envelope)
        }

    @Test
    fun clearedEnvelope_isNotReseededFromTheRoute() =
        runTest(testDispatcher) {
            val fixture = fixture("envelope" to foodEnvelope)
            content(fixture.vm)
            fixture.vm.onEnvelopeSelected(null)
            advanceUntilIdle()

            assertNull(content(fixture.recreate()).filters.envelope)
        }

    @Test
    fun staleEnvelopeId_meansNoFilter() =
        runTest(testDispatcher) {
            val content = content(fixture("envelope" to "deleted-envelope").vm)

            assertNull(content.filters.envelope)
            assertNull(content.categoryScope)
            assertEquals(setOf("lunch", "rent", "mug"), content.ids())
        }

    @Test
    fun envelopeDeletedAfterSelection_stopsFiltering() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)
            vm.onEnvelopeSelected(foodEnvelope)
            advanceUntilIdle()
            assertEquals(setOf("lunch"), current(vm).ids())

            envelopeRepo.deleteEnvelope(foodEnvelope)
            advanceUntilIdle()

            // Selecting an envelope moved to the Expenses tab, which clearing it does not undo.
            assertNull(current(vm).filters.envelope)
            assertEquals(setOf("lunch", "rent", "mug"), current(vm).ids())
        }

    @Test
    fun envelopeChipActive_breakdownCategoryTotals_sumOnlyVisibleExpenses() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)

            vm.onEnvelopeSelected(foodEnvelope)
            advanceUntilIdle()

            val nonZero =
                current(vm)
                    .analytics.breakdown.categoryTotals
                    .filter { it.totalCents != 0L }
            assertEquals(listOf(BuiltInCategories.FOOD.id), nonZero.map { it.category.id })
            assertEquals(900L, nonZero.sumOf { it.totalCents })
        }

    @Test
    fun chips_listOnlyEnvelopesWithSpend_andNoEnvelopeWhenItHasSpend() =
        runTest(testDispatcher) {
            val vm = fixture(expenses = listOf(lunch, mug)).vm

            assertEquals(
                listOf(foodChip(900L), noEnvChip(300L)),
                content(vm).analytics.envelopeChips,
            )
        }

    @Test
    fun chips_omitNoEnvelope_whenEverythingIsEnveloped() =
        runTest(testDispatcher) {
            val vm = fixture(expenses = listOf(lunch, rent)).vm

            assertEquals(
                listOf(foodChip(900L), billsChip(5_000L)),
                content(vm).analytics.envelopeChips,
            )
        }

    @Test
    fun chips_areEmpty_withoutSpend() =
        runTest(testDispatcher) {
            val content = content(fixture(expenses = emptyList()).vm)

            assertEquals(emptyList<EnvelopeChip>(), content.analytics.envelopeChips)
            assertEquals(setOf(foodEnvelope, billsEnvelope), content.analytics.envelopeIds)
        }

    @Test
    fun chips_keepTheSelectedEnvelope_atZeroSpend() =
        runTest(testDispatcher) {
            val vm = fixture(expenses = listOf(mug)).vm
            content(vm)

            vm.onEnvelopeSelected(billsEnvelope)
            advanceUntilIdle()

            assertEquals(
                listOf(billsChip(0L), noEnvChip(300L)),
                current(vm).analytics.envelopeChips,
            )
        }

    @Test
    fun chips_keepSelectedNoEnvelope_atZeroSpend() =
        runTest(testDispatcher) {
            val vm = fixture(expenses = listOf(lunch)).vm
            content(vm)

            vm.onEnvelopeSelected(NO_ENVELOPE)
            advanceUntilIdle()

            assertEquals(
                listOf(foodChip(900L), noEnvChip(0L)),
                current(vm).analytics.envelopeChips,
            )
        }

    @Test
    fun chips_ignoreThePeriodsOtherMonths() =
        runTest(testDispatcher) {
            val september = lunch.copy(id = "sepLunch", date = noonOn(2026, 9, 10))
            val vm = fixture(expenses = listOf(september, mug)).vm
            content(vm)

            assertEquals(listOf(noEnvChip(300L)), current(vm).analytics.envelopeChips)

            vm.periodActions.onSelect(Period.Month(YearMonth.of(2026, 9)))
            advanceUntilIdle()

            assertEquals(listOf(foodChip(900L)), current(vm).analytics.envelopeChips)
        }

    @Test
    fun routePeriodAndEnvelope_combine() =
        runTest(testDispatcher) {
            val september = lunch.copy(id = "sepLunch", date = noonOn(2026, 9, 10))
            val content =
                content(
                    fixture(
                        "period" to Period.Month(YearMonth.of(2026, 9)).encode(),
                        "envelope" to foodEnvelope,
                        expenses = listOf(september, lunch, mug),
                    ).vm,
                )

            assertEquals(setOf("sepLunch"), content.ids())
        }
}

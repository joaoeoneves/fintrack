package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.model.encode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Period behavior (month, rolling and custom) and the period totals of [ActivityViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelPeriodTest : ActivityViewModelTestBase() {
    private val aug = expense("aug", 100L, noonOn(2026, 8, 15))
    private val sep = expense("sep", 200L, noonOn(2026, 9, 15))
    private val oct = expense("oct", 300L, noonOn(2026, 10, 15))
    private val sepIncome = income("sepIncome", 5_000L, noonOn(2026, 9, 1))
    private val octIncome = income("octIncome", 7_000L, noonOn(2026, 10, 1))
    private val expenses = listOf(aug, sep, oct)
    private val incomes = listOf(sepIncome, octIncome)

    private fun fixture(vararg p: Pair<String, Any?>) = Fixture(expenses, incomes, args(*p))

    private fun ActivityUiState.Content.ids() = items.map { it.id }.toSet()

    @Test
    fun missingPeriod_defaultsToCurrentMonth() =
        runTest(testDispatcher) {
            val content = content(fixture().vm)

            assertEquals(Period.CurrentMonth, content.period)
            assertEquals(setOf("oct", "octIncome"), content.ids())
            assertFalse(content.canGoToNextMonth)
        }

    @Test
    fun routePeriod_month_seedsThatMonth() =
        runTest(testDispatcher) {
            val content = content(fixture("period" to Period.Month(YearMonth.of(2026, 9)).encode()).vm)

            assertEquals(Period.Month(YearMonth.of(2026, 9)), content.period)
            assertEquals(setOf("sep", "sepIncome"), content.ids())
            assertTrue(content.canGoToNextMonth)
        }

    @Test
    fun routePeriod_rollingAndCustom_areKept() =
        runTest(testDispatcher) {
            val rolling = Period.Rolling(TimeRange.THREE_MONTHS)
            val rollingContent = content(fixture("period" to rolling.encode()).vm)
            assertEquals(rolling, rollingContent.period)
            assertEquals(setOf("aug", "sep", "oct", "sepIncome", "octIncome"), rollingContent.ids())
            assertFalse(rollingContent.canGoToNextMonth)

            val custom = Period.Custom(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30))
            val customContent = content(fixture("period" to custom.encode()).vm)
            assertEquals(custom, customContent.period)
            assertEquals(setOf("aug", "sep", "sepIncome"), customContent.ids())
        }

    @Test
    fun routePeriod_nonsense_fallsBackToCurrentMonth() =
        runTest(testDispatcher) {
            val content = content(fixture("period" to "nonsense").vm)

            assertEquals(Period.CurrentMonth, content.period)
            assertEquals(setOf("oct", "octIncome"), content.ids())
        }

    @Test
    fun onPeriodSelected_rolling_isKept() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)

            vm.periodActions.onSelect(Period.Rolling(TimeRange.ONE_YEAR))
            advanceUntilIdle()

            assertEquals(Period.Rolling(TimeRange.ONE_YEAR), current(vm).period)
            assertEquals(expenses.map { it.id }.toSet() + incomes.map { it.id }, current(vm).ids())
        }

    @Test
    fun onPeriodSelected_custom_isInclusiveOfBothEnds() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)
            val custom = Period.Custom(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15))

            vm.periodActions.onSelect(custom)
            advanceUntilIdle()

            assertEquals(custom, current(vm).period)
            assertEquals(setOf("sep", "oct", "octIncome"), current(vm).ids())
        }

    @Test
    fun onPeriodSelected_month_switchesMonth() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)

            vm.periodActions.onSelect(Period.Month(YearMonth.of(2026, 8)))
            advanceUntilIdle()

            assertEquals(setOf("aug"), current(vm).ids())
        }

    @Test
    fun previousAndNext_navigateMonths_andReQuery() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)

            vm.periodActions.onPrevious()
            advanceUntilIdle()
            assertEquals(Period.Month(YearMonth.of(2026, 9)), current(vm).period)
            assertEquals(setOf("sep", "sepIncome"), current(vm).ids())
            assertTrue(current(vm).canGoToNextMonth)

            vm.periodActions.onNext()
            advanceUntilIdle()
            assertEquals(Period.CurrentMonth, current(vm).period)
            assertEquals(setOf("oct", "octIncome"), current(vm).ids())
            assertFalse(current(vm).canGoToNextMonth)
        }

    @Test
    fun userSelection_isStoredSeparately_andSurvivesRecreation() =
        runTest(testDispatcher) {
            val fixture = fixture("period" to "current")
            content(fixture.vm)
            fixture.vm.periodActions.onPrevious()
            advanceUntilIdle()

            assertEquals("current", fixture.handle.get<String>("period"))
            assertEquals("month:2026-09", fixture.handle.get<String>("selectedPeriod"))
            assertEquals(Period.Month(YearMonth.of(2026, 9)), content(fixture.recreate()).period)
        }

    @Test
    fun periodChange_keepsFilters() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)
            vm.onTypeSelected(ActivityType.INCOME)

            vm.periodActions.onPrevious()
            advanceUntilIdle()

            assertEquals(setOf("sepIncome"), current(vm).ids())
        }

    @Test
    fun list_followsQueryAndType() =
        runTest(testDispatcher) {
            val fixture =
                Fixture(
                    listOf(expense("a", 1_000L), expense("b", 500L)),
                    listOf(income("i", 4_000L), income("j", 100L)),
                )
            val vm = fixture.vm
            content(vm)
            vm.onQueryChanged("a")
            vm.onTypeSelected(ActivityType.EXPENSE)
            advanceUntilIdle()

            assertEquals(listOf("a"), current(vm).items.map { it.id })
        }

    @Test
    fun emptyPeriod_hasNoItems() =
        runTest(testDispatcher) {
            val content = content(Fixture().vm)

            assertTrue(content.items.isEmpty())
            assertTrue(content.days.isEmpty())
        }

    @Test
    fun zeroAmountEntries_stillListed() =
        runTest(testDispatcher) {
            val content = content(Fixture(listOf(expense("z", 0L)), listOf(income("zi", 0L))).vm)

            assertEquals(2, content.items.size)
        }
}

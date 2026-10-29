package com.joaoeoneves.fintrack.ui.plan.salary

import android.app.Application
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.data.local.IdGenerator
import com.joaoeoneves.fintrack.domain.model.MAX_SALARY_EXTRAS
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryExtraDeductions
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.managedExtraKey
import com.joaoeoneves.fintrack.domain.usecase.ManageSalaryProfile
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.testutil.bonusExtra
import com.joaoeoneves.fintrack.testutil.flexExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

/** The extras part of the salary profile screen: the editor sheet, the cap and what Save persists. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SalaryProfileViewModelExtrasTest {
    private val dispatcher = StandardTestDispatcher()
    private val profiles = FakeSalaryProfileRepository()
    private val income = FakePlannedIncomeRepository()
    private val clock = MutableClock(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    private var counter = 0
    private val ids = IdGenerator { "gen${++counter}" }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val manage get() = ManageSalaryProfile(profiles, income)

    private fun viewModel() = SalaryProfileViewModel(profiles, manage, income, clock, ids)

    private val default = SalaryProfile(baseGrossCents = 100_000)

    private suspend fun TestScope.started(stored: SalaryProfile? = default): SalaryProfileViewModel {
        stored?.let { profiles.saveSalaryProfile(it) }
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.start('.')
        advanceUntilIdle()
        return vm
    }

    private fun SalaryProfileViewModel.content() = uiState.value as SalaryProfileUiState.Content

    private fun TestScope.fill(
        vm: SalaryProfileViewModel,
        name: String = "Prize",
        amount: String = "100",
        cents: Long = 10_000L,
    ) {
        advanceUntilIdle()
        vm.onExtraFormChange(
            vm
                .content()
                .extraEditor!!
                .form
                .copy(name = name, amountText = amount, amountCents = cents),
        )
        advanceUntilIdle()
    }

    @Test
    fun addExtra_opensAnEmptyEditor_paidInTheCurrentMonth() =
        runTest(dispatcher) {
            val vm = started()

            vm.onAddExtra()
            advanceUntilIdle()

            val editor = requireNotNull(vm.content().extraEditor)
            assertTrue(editor.isNew)
            assertEquals(listOf(10), editor.form.months)
            assertEquals(SalaryExtraAmountType.FIXED_CENTS, editor.form.amountType)
            assertEquals(SalaryExtraDeductions.NONE, editor.form.deductions)
            assertFalse(editor.canSave)
        }

    @Test
    fun saveExtra_addsItWithTheGeneratedId_andClosesTheEditor() =
        runTest(dispatcher) {
            val vm = started()
            vm.onAddExtra()
            fill(vm)

            assertTrue(vm.content().extraEditor!!.canSave)
            vm.onSaveExtra()
            advanceUntilIdle()

            val extra =
                vm
                    .content()
                    .form.extras
                    .single()
            assertEquals("gen1", extra.id)
            assertEquals("Prize", extra.name)
            assertEquals(10_000L, extra.amount)
            assertEquals(setOf(10), extra.months)
            assertNull(vm.content().extraEditor)
        }

    @Test
    fun saveExtra_invalid_staysOpen_andUsesNoIdOrChange() =
        runTest(dispatcher) {
            val vm = started()
            vm.onAddExtra()
            fill(vm, name = " ", amount = "abc", cents = 0L)

            vm.onSaveExtra()
            advanceUntilIdle()

            assertNotNull(vm.content().extraEditor)
            assertEquals(setOf(SalaryExtraField.NAME, SalaryExtraField.AMOUNT), vm.content().extraEditor!!.invalid)
            assertTrue(
                vm
                    .content()
                    .form.extras
                    .isEmpty(),
            )
        }

    @Test
    fun editExtra_replacesInPlace_keepingIdAndOrder() =
        runTest(dispatcher) {
            val vm = started(SalaryProfile(baseGrossCents = 100_000, extras = listOf(flexExtra(), bonusExtra())))

            vm.onEditExtra("flex")
            advanceUntilIdle()
            assertFalse(vm.content().extraEditor!!.isNew)
            fill(vm, name = "Renamed", amount = "8")
            vm.onSaveExtra()
            advanceUntilIdle()

            val extras = vm.content().form.extras
            assertEquals(listOf("flex", "bonus"), extras.map { it.id })
            assertEquals("Renamed", extras.first().name)
            assertEquals(80_000L, extras.first().amount)
            assertEquals(0, counter)
        }

    @Test
    fun editUnknownExtra_doesNothing() =
        runTest(dispatcher) {
            val vm = started()

            vm.onEditExtra("nope")
            advanceUntilIdle()

            assertNull(vm.content().extraEditor)
        }

    @Test
    fun dismiss_closesTheEditorWithoutChanges() =
        runTest(dispatcher) {
            val vm = started()
            vm.onAddExtra()
            fill(vm)

            vm.onDismissExtra()
            advanceUntilIdle()

            assertNull(vm.content().extraEditor)
            assertTrue(
                vm
                    .content()
                    .form.extras
                    .isEmpty(),
            )
        }

    @Test
    fun deleteExtra_removesIt_andClosesTheEditor() =
        runTest(dispatcher) {
            val vm = started(SalaryProfile(baseGrossCents = 100_000, extras = listOf(flexExtra(), bonusExtra())))
            vm.onEditExtra("flex")

            vm.onDeleteExtra("flex")
            advanceUntilIdle()

            assertEquals(
                listOf("bonus"),
                vm
                    .content()
                    .form.extras
                    .map { it.id },
            )
            assertNull(vm.content().extraEditor)
        }

    @Test
    fun deleteExtra_emitsExtraDeletedWithIndex_andUndoRestoresAtOldPositionOnce() =
        runTest(dispatcher) {
            val vm = started(SalaryProfile(baseGrossCents = 100_000, extras = listOf(flexExtra(), bonusExtra())))
            val events = mutableListOf<SalaryProfileEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventFlow.collect { events += it } }

            vm.onDeleteExtra("flex")
            advanceUntilIdle()
            val deleted = events.single() as SalaryProfileEvent.ExtraDeleted
            assertEquals("flex", deleted.extra.id)
            assertEquals(0, deleted.index)

            vm.onUndoDeleteExtra(deleted.extra, deleted.index)
            vm.onUndoDeleteExtra(deleted.extra, deleted.index) // no duplicate
            advanceUntilIdle()

            assertEquals(
                listOf("flex", "bonus"),
                vm
                    .content()
                    .form.extras
                    .map { it.id },
            )
        }

    @Test
    fun deleteExtra_unknownId_emitsNothing() =
        runTest(dispatcher) {
            val vm = started(SalaryProfile(baseGrossCents = 100_000, extras = listOf(flexExtra())))
            val events = mutableListOf<SalaryProfileEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventFlow.collect { events += it } }

            vm.onDeleteExtra("nope")
            advanceUntilIdle()

            assertTrue(events.isEmpty())
            assertEquals(
                1,
                vm
                    .content()
                    .form.extras.size,
            )
        }

    @Test
    fun undoDeleteExtra_indexBeyondEnd_appends() =
        runTest(dispatcher) {
            val vm = started(SalaryProfile(baseGrossCents = 100_000, extras = listOf(bonusExtra())))

            vm.onUndoDeleteExtra(flexExtra(), 99)
            advanceUntilIdle()

            assertEquals(
                listOf("bonus", "flex"),
                vm
                    .content()
                    .form.extras
                    .map { it.id },
            )
        }

    @Test
    fun cap_atTen_hidesAddAndMakesItANoOp() =
        runTest(dispatcher) {
            val many = List(MAX_SALARY_EXTRAS) { bonusExtra(id = "e$it", name = "E$it") }
            val vm = started(SalaryProfile(baseGrossCents = 100_000, extras = many))
            assertFalse(vm.content().canAddExtra)

            vm.onAddExtra()
            advanceUntilIdle()

            assertNull(vm.content().extraEditor)
            assertEquals(
                MAX_SALARY_EXTRAS,
                vm
                    .content()
                    .form.extras.size,
            )
        }

    @Test
    fun cap_belowTen_canAdd_andDeletingReopensIt() =
        runTest(dispatcher) {
            val many = List(MAX_SALARY_EXTRAS) { bonusExtra(id = "e$it", name = "E$it") }
            val vm = started(SalaryProfile(baseGrossCents = 100_000, extras = many.take(MAX_SALARY_EXTRAS - 1)))
            assertTrue(vm.content().canAddExtra)

            vm.onAddExtra()
            fill(vm)
            vm.onSaveExtra()
            advanceUntilIdle()
            assertFalse(vm.content().canAddExtra)

            vm.onDeleteExtra("e0")
            advanceUntilIdle()
            assertTrue(vm.content().canAddExtra)
        }

    @Test
    fun netMode_forcesFixedNoDeductionsNoFee() =
        runTest(dispatcher) {
            val vm = started(SalaryProfile(inputMode = SalaryInputMode.NET, netMonthlyCents = 100_000))
            vm.onAddExtra()
            advanceUntilIdle()
            val form = vm.content().extraEditor!!.form
            vm.onExtraFormChange(
                form.copy(
                    name = "Prize",
                    amountCents = 5_000L,
                    amountType = SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM,
                    deductions = SalaryExtraDeductions.IRS_SS,
                    feeText = "5",
                ),
            )

            vm.onSaveExtra()
            advanceUntilIdle()

            val extra =
                vm
                    .content()
                    .form.extras
                    .single()
            assertEquals(SalaryExtraAmountType.FIXED_CENTS, extra.amountType)
            assertEquals(5_000L, extra.amount)
            assertEquals(SalaryExtraDeductions.NONE, extra.deductions)
            assertEquals(0, extra.feePpm)
        }

    @Test
    fun netMode_editingAGrossOnlyExtra_startsBlankAsFixed() =
        runTest(dispatcher) {
            val stored =
                SalaryProfile(inputMode = SalaryInputMode.NET, netMonthlyCents = 100_000, extras = listOf(flexExtra()))
            val vm = started(stored)

            vm.onEditExtra("flex")
            advanceUntilIdle()

            val form = vm.content().extraEditor!!.form
            assertEquals(SalaryExtraAmountType.FIXED_CENTS, form.amountType)
            assertEquals("", form.amountText)
        }

    @Test
    fun save_persistsTheExtras_andGeneratesTheirLines() =
        runTest(dispatcher) {
            val vm = started()
            vm.onAddExtra()
            fill(vm)
            vm.onSaveExtra()
            advanceUntilIdle()

            vm.onSave(SalaryLineLabels())
            advanceUntilIdle()

            val saved = requireNotNull(profiles.profile)
            assertEquals(listOf("Prize"), saved.extras.map { it.name })
            val line = income.lines.single { it.id == managedExtraKey("gen1", 10) }
            assertEquals("Prize", line.label)
            assertEquals(10_000L, line.amountCents)
        }

    @Test
    fun extrasNotSavedYet_areNotPersisted() =
        runTest(dispatcher) {
            val vm = started()
            vm.onAddExtra()
            fill(vm)
            vm.onSaveExtra()
            advanceUntilIdle()

            assertTrue(requireNotNull(profiles.profile).extras.isEmpty())
        }

    @Test
    fun breakdown_includesTheExtras() =
        runTest(dispatcher) {
            val vm = started(SalaryProfile(baseGrossCents = 100_000, extras = listOf(flexExtra())))

            assertEquals(
                98_000L,
                vm
                    .content()
                    .breakdown!!
                    .extras
                    .single()
                    .totalCents,
            )
        }
}

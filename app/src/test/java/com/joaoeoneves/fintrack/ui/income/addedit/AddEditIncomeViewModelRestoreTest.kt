package com.joaoeoneves.fintrack.ui.income.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.testutil.deleteTransactionFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock

/** The income form's fields come back from the [SavedStateHandle] after process death. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditIncomeViewModelRestoreTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun snapshot(id: String?) = IncomeFormSnapshot(id, "Freelance", 75_000L, 1_760_000_000_000L, "invoice 7")

    private fun json(snapshot: IncomeFormSnapshot) = Json.encodeToString(IncomeFormSnapshot.serializer(), snapshot)

    private fun form(saved: String): IncomeFormState {
        val repo = FakeIncomeRepository()
        val vm =
            AddEditIncomeViewModel(
                repo,
                deleteTransactionFor(incomes = repo),
                SavedStateHandle(mapOf("income_form" to saved)),
                Clock.systemDefaultZone(),
            )
        testDispatcher.scheduler.advanceUntilIdle()
        return (vm.uiState.value as AddEditIncomeUiState.Ready).form
    }

    @Test
    fun aSnapshotOfTheSameNewIncome_isRestored() {
        val form = form(json(snapshot(null)))

        assertEquals("Freelance", form.source)
        assertEquals(75_000L, form.amountCents)
        assertEquals(1_760_000_000_000L, form.date.toEpochMilli())
        assertEquals("invoice 7", form.note)
    }

    @Test
    fun aSnapshotOfAnotherIncome_isNotRestored() {
        val form = form(json(snapshot("someone-else")))

        assertEquals("", form.source)
        assertEquals(0L, form.amountCents)
    }

    @Test
    fun anUnreadableSnapshot_isIgnored() {
        val form = form("]]")

        assertEquals("", form.source)
        assertEquals(0L, form.amountCents)
    }
}

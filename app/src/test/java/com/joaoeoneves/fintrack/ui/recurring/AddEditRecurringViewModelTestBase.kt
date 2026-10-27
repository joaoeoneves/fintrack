package com.joaoeoneves.fintrack.ui.recurring

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.testutil.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
abstract class AddEditRecurringViewModelTestBase {
    protected val testDispatcher = StandardTestDispatcher()
    protected val clock = MutableClock(Instant.parse("2026-03-15T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    protected fun viewModel(
        repo: FakeRecurringRepository = FakeRecurringRepository(),
        categories: FakeCategoryRepository = FakeCategoryRepository(),
        id: String? = null,
    ) = AddEditRecurringViewModel(
        repo,
        categories,
        clock,
        SavedStateHandle(if (id != null) mapOf("recurringId" to id) else emptyMap()),
    )

    protected fun AddEditRecurringViewModel.ready() = uiState.value as AddEditRecurringUiState.Ready

    protected fun AddEditRecurringViewModel.fill() {
        onFormChanged(ready().form.copy(name = "Gym", amountText = "20,00"))
    }
}

package com.joaoeoneves.fintrack.ui.income.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MAX_NOTE_LENGTH
import com.joaoeoneves.fintrack.testutil.deleteTransactionFor
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [AddEditIncomeViewModel] source/note length limits. Split out of [AddEditIncomeViewModelTest]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditIncomeViewModelLimitsTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val emoji = "😀"

    private fun viewModel() =
        AddEditIncomeViewModel(
            FakeIncomeRepository(),
            deleteTransactionFor(),
            SavedStateHandle(),
            java.time.Clock.systemDefaultZone(),
        )

    private fun AddEditIncomeViewModel.form() = (uiState.value as AddEditIncomeUiState.Ready).form

    @Test
    fun onSourceChanged_atLimit_rejectsFurtherTyping() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            val atLimit = "s".repeat(MAX_NAME_LENGTH)

            vm.onSourceChanged(atLimit)
            vm.onSourceChanged(atLimit + "t")

            assertEquals(atLimit, vm.form().source)
        }

    @Test
    fun onSourceChanged_emojiPasteOverLimit_isTruncatedOnCodePointBoundary() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.onSourceChanged(emoji.repeat(MAX_NAME_LENGTH + 1))

            assertEquals(emoji.repeat(MAX_NAME_LENGTH), vm.form().source)
        }

    @Test
    fun onNoteChanged_pasteOverLimit_isTruncated_andAtLimitFurtherTypingIsRejected() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.onNoteChanged("n".repeat(MAX_NOTE_LENGTH + 10))
            assertEquals("n".repeat(MAX_NOTE_LENGTH), vm.form().note)

            vm.onNoteChanged("n".repeat(MAX_NOTE_LENGTH) + "x")
            assertEquals("n".repeat(MAX_NOTE_LENGTH), vm.form().note)
        }

    @Test
    fun onNoteChanged_withinLimit_isAccepted() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.onNoteChanged("bonus")

            assertEquals("bonus", vm.form().note)
        }
}

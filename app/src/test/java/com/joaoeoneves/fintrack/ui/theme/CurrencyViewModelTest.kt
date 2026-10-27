package com.joaoeoneves.fintrack.ui.theme

import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.testutil.FakeCurrencyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrencyViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun currency_beforeCollection_isEurDefault() =
        runTest(testDispatcher) {
            val viewModel = CurrencyViewModel(FakeCurrencyRepository(CurrencyOption.USD))

            assertEquals(CurrencyOption.EUR, viewModel.currency.value)
        }

    @Test
    fun currency_afterCollection_reflectsRepository() =
        runTest(testDispatcher) {
            val viewModel = CurrencyViewModel(FakeCurrencyRepository(CurrencyOption.USD))
            val job = launch { viewModel.currency.collect {} }
            advanceUntilIdle()

            assertEquals(CurrencyOption.USD, viewModel.currency.value)
            job.cancel()
        }

    @Test
    fun currency_followsRepositoryChanges() =
        runTest(testDispatcher) {
            val repo = FakeCurrencyRepository(CurrencyOption.EUR)
            val viewModel = CurrencyViewModel(repo)
            val job = launch { viewModel.currency.collect {} }
            advanceUntilIdle()

            CurrencyOption.entries.forEach {
                repo.setCurrency(it)
                advanceUntilIdle()
                assertEquals(it, viewModel.currency.value)
            }
            job.cancel()
        }
}

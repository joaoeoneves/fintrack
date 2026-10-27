package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MAX_WORKING_DAYS
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class LocalMonthSettingsRepositoryTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalMonthSettingsRepository(store) }
    private val oct = YearMonth.of(2026, 10)

    private suspend fun settings() = repo.observeMonthSettings().first()

    @Test
    fun observe_empty() =
        runStoreTest {
            assertEquals(emptyList<MonthSettings>(), settings())
        }

    @Test
    fun set_storesOverride_andPersistsToDisk() =
        runStoreTest {
            assertTrue(repo.setWorkingDaysOverride(oct, 18).isSuccess)

            assertEquals(listOf(MonthSettings(oct, 18)), settings())
            assertEquals(listOf(LocalMonthSettings("2026-10", 18)), diskDoc().monthSettings)
        }

    @Test
    fun set_again_replacesInPlace() =
        runStoreTest {
            repo.setWorkingDaysOverride(oct, 18)
            repo.setWorkingDaysOverride(oct, 20)

            assertEquals(listOf(MonthSettings(oct, 20)), settings())
        }

    @Test
    fun setNull_deletesOnlyThatMonth() =
        runStoreTest {
            repo.setWorkingDaysOverride(oct, 18)
            repo.setWorkingDaysOverride(YearMonth.of(2026, 11), 10)

            assertTrue(repo.setWorkingDaysOverride(oct, null).isSuccess)

            assertEquals(listOf(MonthSettings(YearMonth.of(2026, 11), 10)), settings())
        }

    @Test
    fun setNull_withoutOverride_isNoOpSuccess() =
        runStoreTest {
            assertTrue(repo.setWorkingDaysOverride(oct, null).isSuccess)
            assertEquals(emptyList<MonthSettings>(), settings())
        }

    @Test
    fun zeroAndMax_accepted_outOfRangeFailsWithoutWriting() =
        runStoreTest {
            assertTrue(repo.setWorkingDaysOverride(oct, 0).isSuccess)
            assertTrue(repo.setWorkingDaysOverride(oct, MAX_WORKING_DAYS).isSuccess)
            assertTrue(repo.setWorkingDaysOverride(oct, MAX_WORKING_DAYS + 1).isFailure)
            assertTrue(repo.setWorkingDaysOverride(oct, -1).isFailure)

            assertEquals(listOf(MonthSettings(oct, MAX_WORKING_DAYS)), settings())
        }

    @Test
    fun unparsableStoredMonth_isIgnored() =
        runStoreTest {
            val stored = listOf(LocalMonthSettings("bad", 5), LocalMonthSettings("2026-10", 7))
            store.update { it.copy(monthSettings = stored) to Unit }

            assertEquals(listOf(MonthSettings(oct, 7)), settings())
        }
}

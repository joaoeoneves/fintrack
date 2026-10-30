package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
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
    private val nov = YearMonth.of(2026, 11)

    private suspend fun settings() = repo.observeMonthSettings().first()

    @Test
    fun observe_empty() =
        runStoreTest {
            assertEquals(emptyList<MonthSettings>(), settings())
        }

    @Test
    fun setDaysOff_storesRecord_andPersistsToDisk() =
        runStoreTest {
            assertTrue(repo.setDaysOff(oct, 3).isSuccess)

            assertEquals(listOf(MonthSettings(oct, 3)), settings())
            assertEquals(listOf(LocalMonthSettings("2026-10", 3)), diskDoc().monthSettings)
        }

    @Test
    fun setDaysOff_again_replacesInPlace() =
        runStoreTest {
            repo.setDaysOff(oct, 3)
            repo.setDaysOff(oct, 5)

            assertEquals(listOf(MonthSettings(oct, 5)), settings())
        }

    @Test
    fun setDaysOff_zeroWithoutRecord_isNoOpSuccess() =
        runStoreTest {
            assertTrue(repo.setDaysOff(oct, 0).isSuccess)

            assertEquals(emptyList<MonthSettings>(), settings())
        }

    @Test
    fun setDaysOff_zero_deletesOnlyThatMonth() =
        runStoreTest {
            repo.setDaysOff(oct, 3)
            repo.setDaysOff(nov, 2)

            assertTrue(repo.setDaysOff(oct, 0).isSuccess)

            assertEquals(listOf(MonthSettings(nov, 2)), settings())
            assertEquals(listOf(LocalMonthSettings("2026-11", 2)), diskDoc().monthSettings)
        }

    @Test
    fun daysOff_outOfRange_failsWithoutWriting() =
        runStoreTest {
            repo.setDaysOff(oct, MAX_DAYS_OFF)

            assertTrue(repo.setDaysOff(oct, MAX_DAYS_OFF + 1).isFailure)
            assertTrue(repo.setDaysOff(oct, -1).isFailure)

            assertEquals(listOf(MonthSettings(oct, MAX_DAYS_OFF)), settings())
        }

    @Test
    fun unparsableStoredMonth_isIgnored() =
        runStoreTest {
            val stored = listOf(LocalMonthSettings("bad", 5), LocalMonthSettings("2026-10", 7))
            store.update { it.copy(monthSettings = stored) to Unit }

            assertEquals(listOf(MonthSettings(oct, 7)), settings())
        }
}

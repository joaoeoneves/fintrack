package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
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

    private val nov = YearMonth.of(2026, 11)

    @Test
    fun setDaysOff_createsDaysOffOnlyRecord() =
        runStoreTest {
            assertTrue(repo.setDaysOff(oct, 3).isSuccess)

            assertEquals(listOf(MonthSettings(oct, null, 3)), settings())
            assertEquals(listOf(LocalMonthSettings("2026-10", null, 3)), diskDoc().monthSettings)
        }

    @Test
    fun setDaysOff_zeroWithoutRecord_isNoOpSuccess() =
        runStoreTest {
            assertTrue(repo.setDaysOff(oct, 0).isSuccess)

            assertEquals(emptyList<MonthSettings>(), settings())
        }

    @Test
    fun setDaysOff_zero_deletesDaysOffOnlyRecord() =
        runStoreTest {
            repo.setDaysOff(oct, 3)
            repo.setDaysOff(nov, 2)

            assertTrue(repo.setDaysOff(oct, 0).isSuccess)

            assertEquals(listOf(MonthSettings(nov, null, 2)), settings())
        }

    @Test
    fun bothFields_liveInOneRecord() =
        runStoreTest {
            repo.setWorkingDaysOverride(oct, 15)
            repo.setDaysOff(oct, 4)

            assertEquals(listOf(MonthSettings(oct, 15, 4)), settings())
            assertEquals(listOf(LocalMonthSettings("2026-10", 15, 4)), diskDoc().monthSettings)
        }

    @Test
    fun bothFields_setInEitherOrder_endInTheSameRecord() =
        runStoreTest {
            repo.setDaysOff(oct, 4)
            repo.setWorkingDaysOverride(oct, 15)

            assertEquals(listOf(MonthSettings(oct, 15, 4)), settings())
        }

    @Test
    fun clearingOverride_keepsDaysOff() =
        runStoreTest {
            repo.setWorkingDaysOverride(oct, 15)
            repo.setDaysOff(oct, 4)

            assertTrue(repo.setWorkingDaysOverride(oct, null).isSuccess)

            assertEquals(listOf(MonthSettings(oct, null, 4)), settings())
        }

    @Test
    fun clearingDaysOff_keepsOverride() =
        runStoreTest {
            repo.setWorkingDaysOverride(oct, 15)
            repo.setDaysOff(oct, 4)

            assertTrue(repo.setDaysOff(oct, 0).isSuccess)

            assertEquals(listOf(MonthSettings(oct, 15, 0)), settings())
        }

    @Test
    fun clearingBoth_deletesTheRecord() =
        runStoreTest {
            repo.setWorkingDaysOverride(oct, 15)
            repo.setDaysOff(oct, 4)

            repo.setDaysOff(oct, 0)
            repo.setWorkingDaysOverride(oct, null)

            assertEquals(emptyList<MonthSettings>(), settings())
            assertEquals(emptyList<LocalMonthSettings>(), diskDoc().monthSettings)
        }

    @Test
    fun overrideZero_isAValueNotAClear() =
        runStoreTest {
            repo.setWorkingDaysOverride(oct, 0)

            assertEquals(listOf(MonthSettings(oct, 0)), settings())
        }

    @Test
    fun daysOff_outOfRange_failsWithoutWriting() =
        runStoreTest {
            repo.setDaysOff(oct, MAX_DAYS_OFF)

            assertTrue(repo.setDaysOff(oct, MAX_DAYS_OFF + 1).isFailure)
            assertTrue(repo.setDaysOff(oct, -1).isFailure)

            assertEquals(listOf(MonthSettings(oct, null, MAX_DAYS_OFF)), settings())
        }

    @Test
    fun storedRecordWithoutDaysOff_readsAsZero() =
        runStoreTest {
            store.update { it.copy(monthSettings = listOf(LocalMonthSettings("2026-10", 7))) to Unit }

            assertEquals(0, settings().single().daysOff)
        }
}

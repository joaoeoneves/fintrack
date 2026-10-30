package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
            assertTrue(repo.setDaysOff(listOf(oct), 3, 0).isSuccess)

            assertEquals(listOf(MonthSettings(oct, vacationDays = 3)), settings())
            assertEquals(listOf(LocalMonthSettings("2026-10", vacationDays = 3)), diskDoc().monthSettings)
        }

    @Test
    fun setDaysOff_again_replacesInPlace() =
        runStoreTest {
            repo.setDaysOff(listOf(oct), 3, 0)
            repo.setDaysOff(listOf(oct), 5, 0)

            assertEquals(listOf(MonthSettings(oct, vacationDays = 5)), settings())
        }

    @Test
    fun setDaysOff_zeroWithoutRecord_isNoOpSuccess() =
        runStoreTest {
            assertTrue(repo.setDaysOff(listOf(oct), 0, 0).isSuccess)

            assertEquals(emptyList<MonthSettings>(), settings())
        }

    @Test
    fun setDaysOff_zero_deletesOnlyThatMonth() =
        runStoreTest {
            repo.setDaysOff(listOf(oct), 3, 0)
            repo.setDaysOff(listOf(nov), 2, 0)

            assertTrue(repo.setDaysOff(listOf(oct), 0, 0).isSuccess)

            assertEquals(listOf(MonthSettings(nov, vacationDays = 2)), settings())
            assertEquals(listOf(LocalMonthSettings("2026-11", vacationDays = 2)), diskDoc().monthSettings)
        }

    @Test
    fun daysOff_invalidCounts_throwWithoutWriting() =
        runStoreTest {
            repo.setDaysOff(listOf(oct), MAX_DAYS_OFF, 0)

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.setDaysOff(listOf(oct), MAX_DAYS_OFF + 1, 0) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.setDaysOff(listOf(oct), -1, 0) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.setDaysOff(listOf(oct), MAX_DAYS_OFF, 1) }
            }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.setDaysOff(emptyList(), 1, 0) } }

            assertEquals(listOf(MonthSettings(oct, vacationDays = MAX_DAYS_OFF)), settings())
        }

    @Test
    fun setDaysOff_perType_isStoredPerType() =
        runStoreTest {
            repo.setDaysOff(listOf(oct), 2, 3)

            assertEquals(listOf(MonthSettings(oct, 2, 3)), settings())
            assertEquals(5, settings().single().daysOff)
            assertEquals(listOf(LocalMonthSettings("2026-10", 2, 3)), diskDoc().monthSettings)
        }

    @Test
    fun setDaysOff_manyMonths_writesAllInOneStep_andReplacesExisting() =
        runStoreTest {
            repo.setDaysOff(listOf(nov), 9, 0)

            assertTrue(repo.setDaysOff(listOf(oct, nov, YearMonth.of(2026, 12)), 1, 1).isSuccess)

            val all = settings().sortedBy { it.month }
            assertEquals(listOf(oct, nov, YearMonth.of(2026, 12)), all.map { it.month })
            assertTrue(all.all { it.vacationDays == 1 && it.otherDays == 1 })
        }

    @Test
    fun setDaysOff_allZeroOverManyMonths_clearsOnlyThose() =
        runStoreTest {
            val dec = YearMonth.of(2026, 12)
            repo.setDaysOff(listOf(oct, nov, dec), 2, 0)

            assertTrue(repo.setDaysOff(listOf(oct, nov), 0, 0).isSuccess)

            assertEquals(listOf(MonthSettings(dec, vacationDays = 2)), settings())
        }

    @Test
    fun unparsableStoredMonth_isIgnored() =
        runStoreTest {
            val stored = listOf(LocalMonthSettings("bad", 5), LocalMonthSettings("2026-10", 7))
            store.update { it.copy(monthSettings = stored) to Unit }

            assertEquals(listOf(MonthSettings(oct, vacationDays = 7)), settings())
        }
}

package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.Income
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class LocalAutomationSettingsRepositoryTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalAutomationSettingsRepository(store) }
    private val sep = YearMonth.of(2026, 9)

    private suspend fun settings() = repo.observeAutomationSettings().first()

    private fun income(
        id: String,
        cents: Long = 1_000,
    ) = Income(id, "Salary", cents, Instant.parse("2026-09-30T12:00:00Z"), null)

    @Test
    fun empty_isTheDefaults() =
        runStoreTest {
            assertEquals(AutomationSettings(), settings())
        }

    @Test
    fun enable_withCursor_persists() =
        runStoreTest {
            assertTrue(repo.setPaydayEnabled(true, sep).isSuccess)

            assertEquals(AutomationSettings(true, sep), settings())
            assertEquals(LocalAutomation(true, "2026-09"), diskDoc().automation)
        }

    @Test
    fun disable_keepsTheStoredCursor() =
        runStoreTest {
            repo.setPaydayEnabled(true, sep)

            repo.setPaydayEnabled(false)

            assertEquals(AutomationSettings(false, sep), settings())
        }

    @Test
    fun enableWithoutCursor_keepsNullCursor() =
        runStoreTest {
            repo.setPaydayEnabled(true)

            assertEquals(AutomationSettings(true, null), settings())
        }

    @Test
    fun recordPayday_storesIncomes_andMovesTheCursorAtomically() =
        runStoreTest {
            repo.setPaydayEnabled(true, YearMonth.of(2026, 8))

            assertTrue(repo.recordPayday(sep, listOf(income("pay_a_202609"), income("pay_b_202609", 5))).isSuccess)

            assertEquals(listOf("pay_a_202609", "pay_b_202609"), diskDoc().income.map { it.id })
            assertEquals(sep, settings().paydayRecordedThrough)
            assertTrue(settings().paydayEnabled)
        }

    @Test
    fun recordPayday_sameIdAgain_replacesInsteadOfDuplicating() =
        runStoreTest {
            repo.recordPayday(sep, listOf(income("pay_a_202609", 1)))
            repo.recordPayday(sep, listOf(income("pay_a_202609", 2)))

            assertEquals(listOf(2L), diskDoc().income.map { it.amountCents })
        }

    @Test
    fun recordPayday_noIncomes_onlyMovesTheCursor() =
        runStoreTest {
            assertTrue(repo.recordPayday(sep, emptyList()).isSuccess)

            assertTrue(diskDoc().income.isEmpty())
            assertEquals(sep, settings().paydayRecordedThrough)
        }

    @Test
    fun recordPayday_invalidIncome_failsAndChangesNothing() =
        runStoreTest {
            repo.setPaydayEnabled(true, YearMonth.of(2026, 8))

            val result = repo.recordPayday(sep, listOf(income("pay_a_202609", -5)))

            assertTrue(result.isFailure)
            assertTrue(diskDoc().income.isEmpty())
            assertEquals(YearMonth.of(2026, 8), settings().paydayRecordedThrough)
        }

    @Test
    fun unreadableStoredCursor_readsAsNever() =
        runStoreTest {
            store.update { it.copy(automation = LocalAutomation(true, "garbage")) to Unit }

            assertEquals(AutomationSettings(true, null), settings())
        }

    @Test
    fun v11File_loadsWithoutAutomation_andRoundTrips() =
        runStoreTest {
            dataFile.writeText("""{"schemaVersion":11,"revision":3}""")

            assertNull(newStore().data.first().automation)
            assertTrue(newStore().data.first().isEmpty)

            val s = newStore()
            s.update { it.copy(automation = LocalAutomation(true, "2026-09")) to Unit }
            assertEquals(LocalAutomation(true, "2026-09"), diskDoc().automation)
            assertTrue(!s.data.first().isEmpty)
        }
}

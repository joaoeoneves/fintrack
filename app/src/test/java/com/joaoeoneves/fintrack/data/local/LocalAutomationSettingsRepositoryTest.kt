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
    fun updateLineCursors_setsAndPersists() =
        runStoreTest {
            assertTrue(repo.updateLineCursors(set = mapOf("a" to sep, "b" to YearMonth.of(2026, 8))).isSuccess)

            assertEquals(mapOf("a" to sep, "b" to YearMonth.of(2026, 8)), settings().lineRecordedThrough)
            assertEquals(mapOf("a" to "2026-09", "b" to "2026-08"), diskDoc().automation?.lineThrough)
        }

    @Test
    fun updateLineCursors_removesAndOverwrites() =
        runStoreTest {
            repo.updateLineCursors(set = mapOf("a" to sep, "b" to sep))

            repo.updateLineCursors(set = mapOf("a" to YearMonth.of(2026, 10)), remove = setOf("b"))

            assertEquals(mapOf("a" to YearMonth.of(2026, 10)), settings().lineRecordedThrough)
        }

    @Test
    fun recordPayday_storesIncomes_andMovesTheCursorsAtomically() =
        runStoreTest {
            repo.updateLineCursors(set = mapOf("a" to YearMonth.of(2026, 8), "c" to YearMonth.of(2026, 8)))

            val incomes = listOf(income("pay_a_202609"), income("pay_b_202609", 5))
            assertTrue(repo.recordPayday(incomes, mapOf("a" to sep, "b" to sep)).isSuccess)

            assertEquals(listOf("pay_a_202609", "pay_b_202609"), diskDoc().income.map { it.id })
            assertEquals(
                mapOf("a" to sep, "b" to sep, "c" to YearMonth.of(2026, 8)),
                settings().lineRecordedThrough,
            )
        }

    @Test
    fun recordPayday_sameIdAgain_replacesInsteadOfDuplicating() =
        runStoreTest {
            repo.recordPayday(listOf(income("pay_a_202609", 1)), mapOf("a" to sep))
            repo.recordPayday(listOf(income("pay_a_202609", 2)), mapOf("a" to sep))

            assertEquals(listOf(2L), diskDoc().income.map { it.amountCents })
        }

    @Test
    fun recordPayday_noIncomes_onlyMovesTheCursors() =
        runStoreTest {
            assertTrue(repo.recordPayday(emptyList(), mapOf("a" to sep)).isSuccess)

            assertTrue(diskDoc().income.isEmpty())
            assertEquals(mapOf("a" to sep), settings().lineRecordedThrough)
        }

    @Test
    fun recordPayday_invalidIncome_failsAndChangesNothing() =
        runStoreTest {
            repo.updateLineCursors(set = mapOf("a" to YearMonth.of(2026, 8)))

            val result = repo.recordPayday(listOf(income("pay_a_202609", -5)), mapOf("a" to sep))

            assertTrue(result.isFailure)
            assertTrue(diskDoc().income.isEmpty())
            assertEquals(mapOf("a" to YearMonth.of(2026, 8)), settings().lineRecordedThrough)
        }

    @Test
    fun unreadableStoredCursors_readAsNever() =
        runStoreTest {
            store.update {
                it.copy(automation = LocalAutomation(mapOf("a" to "nope", "b" to "2026-09"))) to Unit
            }

            assertEquals(AutomationSettings(mapOf("b" to sep)), settings())
        }

    @Test
    fun malformedLineThrough_isRejectedByValidation() {
        val bad = LocalAutomation(lineThrough = mapOf("a" to "2026/09"))
        val blank = LocalAutomation(lineThrough = mapOf(" " to "2026-09"))
        val tooMany = LocalAutomation(lineThrough = (0..64).associate { "l$it" to "2026-09" })

        assertNull(bad.validOrNull())
        assertNull(blank.validOrNull())
        assertNull(tooMany.validOrNull())
        assertEquals(
            LocalAutomation(lineThrough = mapOf("a" to "2026-09")),
            LocalAutomation(lineThrough = mapOf("a" to "2026-09")).validOrNull(),
        )
    }

    @Test
    fun v15File_withThePaydaySwitch_decodesIgnoringIt() =
        runStoreTest {
            val automation = """{"paydayEnabled":true,"paydayThrough":"2026-09"}"""
            dataFile.writeText("""{"schemaVersion":15,"revision":3,"automation":$automation}""")

            val loaded = LocalAutomationSettingsRepository(newStore()).observeAutomationSettings().first()
            assertEquals(AutomationSettings(), loaded)
        }

    @Test
    fun v11File_loadsWithoutAutomation_andRoundTrips() =
        runStoreTest {
            dataFile.writeText("""{"schemaVersion":11,"revision":3}""")

            assertNull(newStore().data.first().automation)
            assertTrue(newStore().data.first().isEmpty)

            val s = newStore()
            s.update { it.copy(automation = LocalAutomation(mapOf("a" to "2026-09"))) to Unit }
            assertEquals(LocalAutomation(mapOf("a" to "2026-09")), diskDoc().automation)
            assertTrue(!s.data.first().isEmpty)
        }
}

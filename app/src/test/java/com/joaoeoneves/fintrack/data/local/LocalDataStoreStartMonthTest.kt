package com.joaoeoneves.fintrack.data.local

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.AmountVersion
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** A v18 guest file gains start months on read; moving a budget keeps its timing. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalDataStoreStartMonthTest : LocalStoreTestBase() {
    private val v18File =
        """{"schemaVersion":18,"revision":4,
        |"expenses":[{"id":"e1","name":"x","amountCents":100,"categoryId":"FOOD","date":"2026-04-10T12:00:00Z"}],
        |"budgets":[{"categoryId":"FOOD","limitCents":500}],
        |"plannedIncome":[{"id":"p","label":"Pay","amountCents":1000,"order":0}]}
        """.trimMargin()

    @Test
    fun v18File_getsStartMonthsOnRead_withoutLosingData() =
        runStoreTest {
            dataFile.writeText(v18File)

            val doc = newStore().data.first()

            assertEquals("2026-04", doc.budgets.single().startMonth)
            assertEquals("2026-04", doc.plannedIncome.single().startMonth)
            // Only upgraded in memory to the start-month schema; the next write stamps the current version.
            assertEquals(START_MONTH_SCHEMA_VERSION, doc.schemaVersion)
            assertEquals(4L, doc.revision)
            assertEquals(1, doc.expenses.size)
            assertTrue(filesInDir().none { it.contains("corrupt") })
        }

    @Test
    fun v18File_isNotRewrittenOnRead() =
        runStoreTest {
            dataFile.writeText(v18File)

            newStore().data.first()

            assertEquals(v18File, dataFile.readText())
        }

    @Test
    fun v18File_nextWritePersistsV22() =
        runStoreTest {
            dataFile.writeText(v18File)
            val store = newStore()

            assertTrue(store.update { it.copy(budgets = it.budgets + LocalBudget("RENT", 1)) to Unit }.isSuccess)

            val onDisk = diskDoc()
            assertEquals(22, onDisk.schemaVersion)
            assertEquals("2026-04", onDisk.budgets.first { it.categoryId == "FOOD" }.startMonth)
        }

    @Test
    fun moveBudget_keepsStartMonthAndVersions() =
        runStoreTest {
            val store = newStore()
            val versions = listOf(LocalAmountVersion("2026-03", 400), LocalAmountVersion("2026-07", 500))
            store.update {
                it.copy(budgets = listOf(LocalBudget("FOOD", 500, startMonth = "2026-03", versions = versions))) to Unit
            }
            val budgets = LocalBudgetRepository(store)

            assertTrue(budgets.moveBudget(CategoryId("FOOD"), CategoryId("SHOPPING")).getOrThrow())

            val moved = budgets.observeBudgets().first().single()
            assertEquals(
                Budget(
                    CategoryId("SHOPPING"),
                    500,
                    startMonth = YearMonth.of(2026, 3),
                    versions =
                        listOf(
                            AmountVersion(YearMonth.of(2026, 3), 400),
                            AmountVersion(YearMonth.of(2026, 7), 500),
                        ),
                ),
                moved,
            )
        }
}

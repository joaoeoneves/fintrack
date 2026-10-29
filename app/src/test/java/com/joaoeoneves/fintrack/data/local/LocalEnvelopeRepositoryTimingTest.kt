package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** The group editor re-saves what it does not show (start month, versions) and never touches the budgets. */
class LocalEnvelopeRepositoryTimingTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val groups by lazy { LocalEnvelopeRepository(store, SequenceIds("g")) }
    private val budgets by lazy { LocalBudgetRepository(store) }
    private val march = YearMonth.of(2026, 3)
    private val june = YearMonth.of(2026, 6)
    private val september = YearMonth.of(2026, 9)

    private fun edit(
        id: String?,
        name: String,
        amount: Long?,
        from: YearMonth,
        fundedBy: String? = null,
    ) = EnvelopeEdit(id, name, amount, fundedBy, emptySet(), emptyMap(), from)

    private suspend fun only() = groups.observeEnvelopes().first().single()

    @Test
    fun newEnvelope_startsAtTheEffectiveMonth() =
        runStoreTest {
            groups.saveEnvelope(edit(null, "Food", 5_000, march))

            assertEquals(march, only().startMonth)
            assertEquals(5_000L, only().amountCents)
        }

    @Test
    fun renameAndFunding_keepTheAmountStartMonthAndVersions() =
        runStoreTest {
            groups.saveEnvelope(edit(null, "Food", 5_000, march))
            groups.saveEnvelope(edit("g1", "Food", 7_000, june))
            val before = only()
            assertTrue(before.versions.isNotEmpty())

            groups.saveEnvelope(edit("g1", "Groceries", 7_000, september, fundedBy = "meal"))

            val after = only()
            assertEquals("Groceries", after.name)
            assertEquals("meal", after.fundedByLineId)
            assertEquals(before.amountCents, after.amountCents)
            assertEquals(march, after.startMonth)
            assertEquals(before.versions, after.versions)
        }

    @Test
    fun saveAndDelete_neverTouchTheBudgets() =
        runStoreTest {
            val food = BuiltInCategories.FOOD.id
            budgets.setBudget(food, 300)
            groups.saveEnvelope(edit(null, "Food", 5_000, march))
            groups.saveEnvelope(edit("g1", "Food 2", 6_000, june))

            assertEquals(listOf(300L), budgets.observeBudgets().first().map { it.limitCents })

            groups.deleteEnvelope("g1")

            assertEquals(listOf(300L), budgets.observeBudgets().first().map { it.limitCents })
        }
}

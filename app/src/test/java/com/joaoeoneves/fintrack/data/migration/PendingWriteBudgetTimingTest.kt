package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.local.LocalAmountVersion
import com.joaoeoneves.fintrack.data.local.LocalBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PendingWriteBudgetTimingTest {
    private fun write(budget: LocalBudget) =
        MigrationPlan(emptyList(), listOf(budget), emptyList(), emptyList(), emptyList(), emptyList(), 0)
            .toBatches()
            .single()
            .single()

    @Test
    fun budgetWrite_carriesStartMonthAndVersions() {
        val versions = listOf(LocalAmountVersion("2026-03", 800), LocalAmountVersion("2026-09", null))
        val data = write(LocalBudget("FOOD", 1_000, startMonth = "2026-03", versions = versions)).data

        assertEquals("2026-03", data["startMonth"])
        assertEquals(
            listOf(
                mapOf("from" to "2026-03", "amountCents" to 800L),
                mapOf("from" to "2026-09", "amountCents" to null),
            ),
            data["versions"],
        )
    }

    @Test
    fun budgetWrite_withoutTiming_omitsTheFields() {
        val data = write(LocalBudget("FOOD", 1_000)).data

        assertFalse("startMonth" in data)
        assertFalse("versions" in data)
    }

    @Test
    fun budgetWrite_unparsableStartMonth_isOmitted() {
        assertFalse("startMonth" in write(LocalBudget("FOOD", 1_000, startMonth = "garbage")).data)
    }
}

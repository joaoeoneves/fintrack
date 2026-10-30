package com.joaoeoneves.fintrack.data.local

import android.app.Application
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Schema 26: sick days folded into other days (upgradedTo26) and the guest file 25 -> 26 step. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalDataStoreV26Test : LocalStoreTestBase() {
    private fun sick(
        month: String,
        vacation: Int = 0,
        other: Int = 0,
        sick: Int? = null,
    ) = LocalMonthSettings(month, vacation, other, legacySickDays = sick)

    @Test
    fun upgradedTo26_addsSickToOther_andDropsTheMarker() {
        val result = listOf(sick("2026-10", vacation = 2, other = 1, sick = 3)).upgradedTo26()

        assertEquals(listOf(LocalMonthSettings("2026-10", 2, 4)), result)
        assertNull(result.single().legacySickDays)
    }

    @Test
    fun upgradedTo26_capsTheTotalAt23_byTrimmingOther() {
        val result = listOf(sick("2026-10", vacation = 20, other = 2, sick = 9)).upgradedTo26()

        assertEquals(LocalMonthSettings("2026-10", 20, 3), result.single())
        assertEquals(23, result.single().totalDays)
    }

    @Test
    fun upgradedTo26_sickOnlyMonth_becomesOtherOnly() {
        assertEquals(listOf(LocalMonthSettings("2026-10", 0, 5)), listOf(sick("2026-10", sick = 5)).upgradedTo26())
    }

    @Test
    fun upgradedTo26_dropsMonthsLeftEmpty_includingSickZero() {
        val result = listOf(sick("2026-09", sick = 0), sick("2026-10", other = 1)).upgradedTo26()

        assertEquals(listOf(LocalMonthSettings("2026-10", 0, 1)), result)
    }

    @Test
    fun upgradedTo26_isIdempotent_andLeavesRecordsWithoutTheMarkerAlone() {
        val records = listOf(sick("2026-10", vacation = 2, other = 1, sick = 3), LocalMonthSettings("2026-11", 4, 4))

        val once = records.upgradedTo26()

        assertEquals(once, once.upgradedTo26())
        assertEquals(LocalMonthSettings("2026-11", 4, 4), once.last())
    }

    @Test
    fun document_stampsTheVersion() {
        val doc = LocalDataDocument(schemaVersion = 25, monthSettings = listOf(sick("2026-10", sick = 1))).upgradedTo26()

        assertEquals(26, doc.schemaVersion)
    }

    @Test
    fun v25GuestFile_foldsSickOnLoad_keepsAwaitingDefaultFalse_andNoSickKeyAfterAWrite() =
        runStoreTest {
            dataFile.writeText(
                """{"schemaVersion":25,"revision":3,"monthSettings":[""" +
                    """{"month":"2026-10","vacationDays":22,"otherDays":1,"sickDays":4},""" +
                    """{"month":"2026-11","sickDays":2}],""" +
                    """"expenses":[{"id":"e","name":"n","amountCents":5,"categoryId":"FOOD","date":"2026-03-10T12:00:00Z"}]}""",
            )
            val store = newStore()

            val doc = store.data.first()

            assertEquals(LOCAL_SCHEMA_VERSION, doc.schemaVersion)
            assertEquals(
                listOf(LocalMonthSettings("2026-10", 22, 1), LocalMonthSettings("2026-11", 0, 2)),
                doc.monthSettings,
            )
            assertFalse(doc.expenses.single().awaitingRefund)
            store.update { it.copy(revision = it.revision) to Unit }
            assertFalse(dataFile.readText().contains("sickDays"))
        }
}

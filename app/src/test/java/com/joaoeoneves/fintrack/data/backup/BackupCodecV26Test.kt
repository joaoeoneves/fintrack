package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalExpense
import com.joaoeoneves.fintrack.data.local.LocalMonthSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Schema 25 -> 26: sick days fold into other days and expenses may carry `awaitingRefund`. */
class BackupCodecV26Test {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""
    private val at = "2026-03-10T12:00:00Z"

    private fun file(
        version: Int,
        months: String = "",
        expenseExtra: String = "",
    ) = """{$header,"schemaVersion":$version,"monthSettings":[$months],"expenses":[{"id":"e1","name":"Shoes",""" +
        """"amountCents":5000,"categoryId":"FOOD","date":"$at"$expenseExtra}]}"""

    private fun decodeMonths(months: String) = BackupCodec.decode(file(25, months)).success().document.monthSettings

    @Test
    fun currentVersionIsTwentySix() {
        assertEquals(26, BACKUP_SCHEMA_VERSION)
    }

    @Test
    fun v25_sickDaysFoldIntoOther() {
        val months = decodeMonths("""{"month":"2026-10","vacationDays":2,"sickDays":3,"otherDays":1}""")

        assertEquals(listOf(LocalMonthSettings("2026-10", vacationDays = 2, otherDays = 4)), months)
    }

    @Test
    fun v25_foldIsCappedAt23_trimmingOther() {
        val months = decodeMonths("""{"month":"2026-10","vacationDays":20,"sickDays":10,"otherDays":2}""")

        assertEquals(23, months.single().totalDays)
        assertEquals(20, months.single().vacationDays)
    }

    @Test
    fun v25_sickOnlyMonth_becomesOther_andEmptyMonthsAreDropped() {
        val months = decodeMonths("""{"month":"2026-10","sickDays":4},{"month":"2026-11","vacationDays":0}""")

        assertEquals(listOf(LocalMonthSettings("2026-10", otherDays = 4)), months)
    }

    @Test
    fun v23_daysOffTotalBecomesVacation_thenFoldRunsOnTheChain() {
        val text = file(23, """{"month":"2026-10","daysOff":3}""")

        val doc = BackupCodec.decode(text).success().document

        assertEquals(listOf(LocalMonthSettings("2026-10", vacationDays = 3)), doc.monthSettings)
    }

    @Test
    fun v25_expenseWithoutTheFlag_readsAsNotAwaiting() {
        val doc = BackupCodec.decode(file(25)).success().document

        assertFalse(doc.expenses.single().awaitingRefund)
        assertEquals(26, doc.schemaVersion)
    }

    @Test
    fun v26_roundTrip_keepsAwaitingRefund_andOmitsFalse() {
        val doc =
            backupDoc(
                expenses =
                    listOf(
                        LocalExpense("a", "Shoes", 5000, "FOOD", at, awaitingRefund = true),
                        backupExpense("b"),
                    ),
            ).copy(monthSettings = listOf(LocalMonthSettings("2026-10", 1, 2)))

        val text = BackupCodec.encode(doc)
        val result = BackupCodec.decode(text).success()

        assertEquals(doc, result.document)
        assertEquals(listOf(true, false), result.document.expenses.map { it.awaitingRefund })
        assertEquals(1, Regex("\"awaitingRefund\"").findAll(text).count())
        assertFalse(text.contains("sickDays"))
    }

    @Test
    fun v26_monthSettingsPassThroughUnchanged() {
        val months = BackupCodec.decode(file(26, """{"month":"2026-10","vacationDays":1,"otherDays":2}"""))
            .success().document.monthSettings

        assertEquals(listOf(LocalMonthSettings("2026-10", 1, 2)), months)
    }

    @Test
    fun v27File_isUnsupported() {
        assertTrue(BackupCodec.decode(file(27)) == BackupDecodeResult.UnsupportedVersion(27))
    }
}

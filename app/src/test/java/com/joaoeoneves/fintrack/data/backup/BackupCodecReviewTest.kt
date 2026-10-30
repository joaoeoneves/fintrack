package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 5.4 fields in the backup file: the review flag on expenses/incomes and the dismissed suggestion keys. */
class BackupCodecReviewTest {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""

    @Test
    fun v21File_decodesWithDefaults() {
        val text =
            """{$header,"schemaVersion":21,
            "expenses":[{"id":"e1","name":"Lunch","amountCents":1000,"categoryId":"FOOD","date":"2026-03-10T12:00:00Z"}],
            "income":[{"id":"i1","source":"Pay","amountCents":1000,"date":"2026-03-10T12:00:00Z"}]}"""

        val result = BackupCodec.decode(text).success()

        assertEquals(0, result.skippedCount)
        val document = result.document
        assertFalse(document.expenses.single().needsReview)
        assertFalse(document.income.single().needsReview)
        assertTrue(result.document.dismissedRecurringSuggestions.isEmpty())
    }

    @Test
    fun v22_roundTrip_keepsBothFields() {
        val doc =
            backupDoc(
                expenses = listOf(backupExpense("e1").copy(needsReview = true), backupExpense("e2")),
                income = listOf(LocalIncome("i1", "Pay", 5000, "2026-03-01T08:00:00Z", needsReview = true)),
            ).copy(dismissedRecurringSuggestions = listOf("netflix", "gym"))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(doc, result.document)
        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        assertEquals(listOf(true, false), result.document.expenses.map { it.needsReview })
    }

    @Test
    fun futureVersionFile_isUnsupported() {
        assertEquals(
            BackupDecodeResult.UnsupportedVersion(BACKUP_SCHEMA_VERSION + 1),
            BackupCodec.decode("""{$header,"schemaVersion":${BACKUP_SCHEMA_VERSION + 1}}"""),
        )
    }

    @Test
    fun blankAndDuplicateDismissals_areCleanedOnDecode_andCappedAt500() {
        val keys = listOf("a", " ", "", "a") + (1..600).map { "k$it" }
        val doc = backupDoc().copy(dismissedRecurringSuggestions = keys)

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        val kept = result.document.dismissedRecurringSuggestions
        assertEquals(500, kept.size)
        assertEquals(kept.distinct(), kept)
        assertEquals("a", kept.first())
        assertTrue(kept.none { it.isBlank() })
    }
}

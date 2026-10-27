package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private fun decode(text: String) = BackupCodec.decode(text)

    @Test
    fun roundTrip_isLossless() {
        val doc =
            backupDoc(
                expenses =
                    listOf(
                        backupExpense("e1", date = "2026-01-31T10:15:30.123456789Z", note = "memo")
                            .copy(isRefund = true),
                        backupExpense("e2", note = null),
                    ),
                income = listOf(backupIncome()),
                budgets = listOf(LocalBudget("FOOD", 5000)),
                categories = listOf(backupCategory(archived = true), backupCategory("FOOD", name = null, order = 3)),
                preferences = BackupPreferences("DARK", "EUR", "ENGLISH"),
            ).copy(appVersion = "1.2")

        val result = decode(BackupCodec.encode(doc)).success()

        assertEquals(doc, result.document)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun roundTrip_nullPreferencesAndAppVersion() {
        val doc = backupDoc()

        val result = decode(BackupCodec.encode(doc)).success()

        assertEquals(null, result.document.preferences)
        assertEquals(null, result.document.appVersion)
        assertEquals(doc, result.document)
    }

    @Test
    fun roundTrip_partialPreferences() {
        val doc = backupDoc(preferences = BackupPreferences(theme = "LIGHT"))

        assertEquals(doc, decode(BackupCodec.encode(doc)).success().document)
    }

    @Test
    fun encode_writesFormatAndVersion() {
        val text = BackupCodec.encode(backupDoc())

        assertTrue(text.contains("\"format\": \"fintrack-backup\""))
        assertTrue(text.contains("\"schemaVersion\": 5"))
    }

    @Test
    fun decode_unknownKeysIgnored() {
        val text =
            """{"format":"fintrack-backup","schemaVersion":1,"exportedAt":"$EXPORTED_AT","future":{"a":1},
            "expenses":[{"id":"e1","name":"x","amountCents":5,"categoryId":"FOOD",
            "date":"2026-03-10T12:00:00Z","extra":1}]}"""

        val result = decode(text).success()

        assertEquals(1, result.document.expenses.size)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun decode_missingFormat_invalid() {
        assertTrue(decode("""{"schemaVersion":1,"exportedAt":"$EXPORTED_AT"}""") is BackupDecodeResult.Invalid)
    }

    @Test
    fun decode_wrongFormat_invalid() {
        val text = """{"format":"other","schemaVersion":1,"exportedAt":"$EXPORTED_AT"}"""

        assertTrue(decode(text) is BackupDecodeResult.Invalid)
    }

    @Test
    fun decode_futureVersion_unsupported() {
        val text = """{"format":"fintrack-backup","schemaVersion":6,"exportedAt":"$EXPORTED_AT"}"""

        assertEquals(BackupDecodeResult.UnsupportedVersion(6), decode(text))
    }

    @Test
    fun decode_versionZeroOrMissingOrNonNumeric_invalid() {
        listOf("0", "-1", "\"x\"").forEach { v ->
            val text = """{"format":"fintrack-backup","schemaVersion":$v,"exportedAt":"$EXPORTED_AT"}"""
            assertTrue("version $v", decode(text) is BackupDecodeResult.Invalid)
        }
        val missing = """{"format":"fintrack-backup","exportedAt":"$EXPORTED_AT"}"""
        assertTrue(decode(missing) is BackupDecodeResult.Invalid)
    }

    @Test
    fun decode_malformedInput_invalid() {
        listOf("", "not json", "[1,2]", "{\"format\":", "null").forEach {
            assertTrue("input '$it'", decode(it) is BackupDecodeResult.Invalid)
        }
    }
}

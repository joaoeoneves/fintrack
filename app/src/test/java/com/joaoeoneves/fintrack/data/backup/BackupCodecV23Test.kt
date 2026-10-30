package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalCategoryGroup
import com.joaoeoneves.fintrack.data.local.LocalMonthSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Schema 22 -> 23: a file from 5.4 decodes cleanly, loses the removed fields and adopts its manual recurring items. */
class BackupCodecV23Test {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""

    private fun recurring(
        id: String,
        autoPost: String,
        handledThrough: String? = "2026-01-15",
    ) = """{"id":"$id","type":"EXPENSE","name":"Rent","amountCents":5000,"categoryId":"FOOD","frequency":"MONTHLY",""" +
        """"dayOfMonth":15,"startDate":"2026-01-01"""" +
        (handledThrough?.let { ""","handledThrough":"$it"""" } ?: "") + "$autoPost}"

    private val v22 =
        """{$header,"schemaVersion":22,"preferences":{"theme":"DARK","currency":"USD","language":"ENGLISH"},""" +
            """"budgets":[{"categoryId":"FOOD","limitCents":500,"skipMonths":[8]}],""" +
            """"goals":[{"id":"g","name":"Trip","targetCents":100,"startDate":"2026-01-01"}],""" +
            """"categoryGroups":[{"id":"e1","name":"Food","order":0,"amountCents":900,"fundedByLineId":"meal",""" +
            """"startMonth":"2026-03","versions":[{"from":"2026-03","amountCents":900}]}],""" +
            """"automation":{"lineThrough":{"p1":"2026-09"},"salaryPaysNextMonth":true},""" +
            """"monthSettings":[{"month":"2026-09","workingDays":15},""" +
            """{"month":"2026-10","workingDays":15,"daysOff":2}],""" +
            """"recurring":[${recurring("manual", ""","autoPost":false""")},""" +
            """${recurring("absent", "")},${recurring("auto", ""","autoPost":true""")}]}"""

    private fun decode() = BackupCodec.decode(v22).success()

    @Test
    fun v22File_decodes_withNothingSkipped_atTheCurrentVersion() {
        val result = decode()

        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun removedFields_areDropped_theRestIsKept() {
        val doc = decode().document

        assertEquals(BackupPreferences("DARK", "ENGLISH"), doc.preferences)
        assertEquals(listOf(LocalCategoryGroup("e1", "Food", 0)), doc.categoryGroups)
        assertEquals(mapOf("p1" to "2026-09"), doc.automation?.lineThrough)
        // The record that only carried the manual working days is gone, the days-off one stays.
        assertEquals(listOf(LocalMonthSettings("2026-10", 2)), doc.monthSettings)
    }

    @Test
    fun manualRecurringItems_haveTheirCursorMovedToTheLastOccurrenceBeforeToday() {
        val doc = decode().document.recurring.associateBy { it.id }

        val today = LocalDate.now()
        listOf("manual", "absent").forEach { id ->
            val cursor = LocalDate.parse(doc.getValue(id).handledThrough!!)
            assertTrue("$id: $cursor not before $today", cursor.isBefore(today))
            assertEquals("$id: monthly on the 15th", 15, cursor.dayOfMonth)
            assertTrue("$id: cursor must be within a month of today", cursor.isAfter(today.minusMonths(2)))
        }
    }

    @Test
    fun autoPostTrueItem_isLeftUntouched() {
        val auto = decode().document.recurring.single { it.id == "auto" }

        assertEquals("2026-01-15", auto.handledThrough)
    }

    @Test
    fun theAutoPostMarker_isNotKept_andReEncodingHasNoRemovedKeys() {
        val doc = decode().document

        doc.recurring.forEach { assertNull(it.autoPost) }
        val text = BackupCodec.encode(doc)
        listOf(
            "budgets",
            "goals",
            "currency",
            "salaryPaysNextMonth",
            "workingDays",
            "fundedByLineId",
            "versions",
            "autoPost",
            "skipMonths",
        ).forEach { assertFalse("$it must not be re-encoded", text.contains("\"$it\"")) }
        assertEquals(doc, BackupCodec.decode(text).success().document)
    }

    @Test
    fun currentVersionFile_isNotUpgradedAgain() {
        val doc = backupDoc().copy(recurring = listOf(backupRecurring("r1", handledThrough = "2026-01-15")))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success().document

        assertEquals("2026-01-15", result.recurring.single().handledThrough)
    }
}

package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.data.local.LocalExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Schema 24 -> 25: expenses may carry `refundOf` / `splitId`; both survive decode, restore and the account plan. */
class BackupCodecV25Test {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""
    private val at = "2026-03-10T12:00:00Z"

    private fun v(
        version: Int,
        extra: String = "",
    ) = """{$header,"schemaVersion":$version,"expenses":[{"id":"e1","name":"Shoes","amountCents":5000,""" +
        """"categoryId":"FOOD","date":"$at"$extra}]}"""

    private val linkedPair =
        listOf(
            backupExpense("orig", amountCents = 5000),
            LocalExpense("ref", "Return", 2000, "FOOD", at, isRefund = true, refundOf = "orig", splitId = "grp"),
        )

    @Test
    fun v24File_decodesWithNoLinks_atCurrentVersion_nothingSkipped() {
        val result = BackupCodec.decode(v(24)).success()

        assertEquals(26, result.document.schemaVersion)
        assertEquals(0, result.skippedCount)
        assertNull(
            result.document.expenses
                .single()
                .refundOf,
        )
        assertNull(
            result.document.expenses
                .single()
                .splitId,
        )
    }

    @Test
    fun v23File_goesThroughTheWholeChain_endingAtTheCurrentVersion() {
        val text = """{$header,"schemaVersion":23,"monthSettings":[{"month":"2026-10","daysOff":2}]}"""

        val doc = BackupCodec.decode(text).success().document

        assertEquals(26, doc.schemaVersion)
        assertEquals(2, doc.monthSettings.single().vacationDays)
    }

    @Test
    fun v25_roundTrip_keepsBothLinks_andOmitsNullOnes() {
        val doc = backupDoc(expenses = linkedPair)

        val text = BackupCodec.encode(doc)
        val result = BackupCodec.decode(text).success()

        assertEquals(doc, result.document)
        assertEquals(listOf(null, "orig"), result.document.expenses.map { it.refundOf })
        assertEquals(listOf(null, "grp"), result.document.expenses.map { it.splitId })
        assertEquals(1, Regex("\"refundOf\"").findAll(text).count())
        assertEquals(1, Regex("\"splitId\"").findAll(text).count())
    }

    @Test
    fun v25File_withLinks_decodes() {
        val result = BackupCodec.decode(v(25, ""","isRefund":true,"refundOf":"orig","splitId":"s1"""")).success()

        val e = result.document.expenses.single()
        assertEquals("orig", e.refundOf)
        assertEquals("s1", e.splitId)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun invalidLinks_dropTheRecord_butDanglingOnesAreKept() {
        val text =
            """{$header,"schemaVersion":25,"expenses":[""" +
                """{"id":"ok","name":"R","amountCents":5,"categoryId":"FOOD",""" +
                """"date":"$at","isRefund":true,"refundOf":"gone"},""" +
                """{"id":"notRefund","name":"X","amountCents":5,"categoryId":"FOOD","date":"$at","refundOf":"o"},""" +
                """{"id":"self","name":"X","amountCents":5,"categoryId":"FOOD",""" +
                """"date":"$at","isRefund":true,"refundOf":"self"},""" +
                """{"id":"badShape","name":"X","amountCents":5,"categoryId":"FOOD",""" +
                """"date":"$at","isRefund":true,"refundOf":"a b"},""" +
                """{"id":"badSplit","name":"X","amountCents":5,"categoryId":"FOOD","date":"$at","splitId":"a/b"}]}"""

        val result = BackupCodec.decode(text).success()

        assertEquals(listOf("ok"), result.document.expenses.map { it.id })
        assertEquals(4, result.skippedCount)
    }

    @Test
    fun accountRestorePlan_keepsIdsAndLinks_andDropsInvalidLinks() {
        val local =
            LocalDataDocument(
                expenses = linkedPair + LocalExpense("bad", "X", 5, "FOOD", at, refundOf = "orig"),
            )

        val plan = restoreMigrationPlanOf(local)

        assertEquals(listOf("orig", "ref"), plan.expenses.map { it.id })
        assertEquals("orig", plan.expenses.single { it.id == "ref" }.refundOf)
        assertEquals("grp", plan.expenses.single { it.id == "ref" }.splitId)
        assertEquals(1, plan.skipped)
    }
}

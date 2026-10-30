package com.joaoeoneves.fintrack.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecValidationTest {
    private fun roundTrip(doc: BackupDocument) = BackupCodec.decode(BackupCodec.encode(doc)).success()

    @Test
    fun invalidRecords_skippedAndCounted() {
        val doc =
            backupDoc(
                expenses =
                    listOf(
                        backupExpense("ok"),
                        backupExpense("zero", amountCents = 0),
                        backupExpense("neg", amountCents = -5),
                        backupExpense("baddate", date = "yesterday"),
                        backupExpense(id = ""),
                        backupExpense("unknowncat", categoryId = "NOPE"),
                    ),
                income =
                    listOf(
                        backupIncome("i1"),
                        backupIncome("i0", amountCents = 0),
                        backupIncome("i2", date = "x"),
                    ),
            )

        val result = roundTrip(doc)

        assertEquals(listOf("ok"), result.document.expenses.map { it.id })
        assertEquals(listOf("i1"), result.document.income.map { it.id })
        assertEquals(5 + 2, result.skippedCount)
    }

    @Test
    fun invalidCategory_droppedWithItsExpenses() {
        val doc =
            backupDoc(
                expenses = listOf(backupExpense("e1", categoryId = "gym"), backupExpense("e2")),
                categories = listOf(backupCategory("gym", name = null)),
            )

        val result = roundTrip(doc)

        assertEquals(emptyList<Any>(), result.document.categories)
        assertEquals(listOf("e2"), result.document.expenses.map { it.id })
        assertEquals(1 + 1, result.skippedCount)
    }

    @Test
    fun validCategory_keepsItsExpenses() {
        val doc =
            backupDoc(
                expenses = listOf(backupExpense("e1", categoryId = "gym")),
                categories = listOf(backupCategory("gym")),
            )

        val result = roundTrip(doc)

        assertEquals(0, result.skippedCount)
        assertEquals(doc.expenses, result.document.expenses)
    }

    @Test
    fun duplicateIds_keepFirst() {
        val doc =
            backupDoc(
                expenses = listOf(backupExpense("e1", amountCents = 1), backupExpense("e1", amountCents = 2)),
                income = listOf(backupIncome("i1", amountCents = 7), backupIncome("i1", amountCents = 8)),
                categories = listOf(backupCategory("gym", name = "A"), backupCategory("gym", name = "B")),
            )

        val result = roundTrip(doc)

        assertEquals(listOf(1L), result.document.expenses.map { it.amountCents })
        assertEquals(listOf(7L), result.document.income.map { it.amountCents })
        assertEquals(listOf("A"), result.document.categories.map { it.name })
        assertEquals(3, result.skippedCount)
    }

    @Test
    fun invalidFirstDuplicate_laterValidOneKept() {
        val doc =
            backupDoc(expenses = listOf(backupExpense("e1", amountCents = 0), backupExpense("e1", amountCents = 2)))

        val result = roundTrip(doc)

        assertEquals(listOf(2L), result.document.expenses.map { it.amountCents })
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun placeholderCategory_decodesWithNoneSkipped() {
        val placeholder =
            backupCategory("deleted-1", name = PLACEHOLDER_CATEGORY_NAME, order = 5, archived = true)
        val doc =
            backupDoc(
                expenses = listOf(backupExpense("e1", categoryId = "deleted-1")),
                categories = listOf(placeholder),
            )

        val result = roundTrip(doc)

        assertEquals(0, result.skippedCount)
        assertEquals(doc, result.document)
    }

    @Test
    fun builtInOverrideWithoutName_isValid() {
        val doc = backupDoc(categories = listOf(backupCategory("FOOD", name = null)))

        assertEquals(0, roundTrip(doc).skippedCount)
    }

    @Test
    fun invalidCategoryShapes_dropped() {
        val doc =
            backupDoc(
                categories =
                    listOf(
                        backupCategory("bad id!"),
                        backupCategory("gym", name = ""),
                        backupCategory("ok"),
                    ),
            )

        val result = roundTrip(doc)

        assertEquals(listOf("ok"), result.document.categories.map { it.id })
        assertEquals(2, result.skippedCount)
    }

    @Test
    fun decode_wrongFieldTypes_invalid() {
        val text =
            """{"format":"fintrack-backup","schemaVersion":1,"exportedAt":"$EXPORTED_AT","expenses":"oops"}"""

        assertTrue(BackupCodec.decode(text) is BackupDecodeResult.Invalid)
    }

    @Test
    fun decode_missingExportedAt_invalid() {
        val result = BackupCodec.decode("""{"format":"fintrack-backup","schemaVersion":1}""")

        assertTrue(result is BackupDecodeResult.Invalid)
    }
}

package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.netCents
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 5.7 `refundOf` / `splitId` links in the guest store: mapping, validation, JSON shape, bulk writes. */
class LocalExpenseLinksTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalExpenseRepository(store, SequenceIds()) }
    private val at = "2026-03-10T12:00:00Z"
    private val known = setOf("FOOD")

    private fun local(
        id: String = "e1",
        isRefund: Boolean = false,
        refundOf: String? = null,
        splitId: String? = null,
    ) = LocalExpense(id, "Lunch", 1000, "FOOD", at, isRefund = isRefund, refundOf = refundOf, splitId = splitId)

    private fun refund(
        id: String,
        of: String? = "orig",
        cents: Long = 400,
    ) = expense(id, amountCents = cents).copy(isRefund = true, refundOf = of)

    // ---- mapping / JSON ----

    @Test
    fun mapping_roundTripsBothLinks() {
        val raw = local(isRefund = true, refundOf = "orig", splitId = "s1")

        val domain = requireNotNull(raw.toDomainOrNull())

        assertEquals("orig", domain.refundOf)
        assertEquals("s1", domain.splitId)
        assertEquals(raw, domain.toLocal())
        assertNull(requireNotNull(local().toDomainOrNull()).refundOf)
    }

    @Test
    fun json_oldRecordWithoutKeys_readsAsNoLinks_andNullsAreNotWritten() {
        val old =
            json.decodeFromString(
                LocalExpense.serializer(),
                """{"id":"e","name":"n","amountCents":5,"categoryId":"FOOD","date":"$at"}""",
            )
        assertNull(old.refundOf)
        assertNull(old.splitId)

        val text = json.encodeToString(LocalExpense.serializer(), old)
        assertFalse(text.contains("refundOf"))
        assertFalse(text.contains("splitId"))
        assertTrue(
            json
                .encodeToString(
                    LocalExpense.serializer(),
                    local(isRefund = true, refundOf = "o", splitId = "s"),
                ).contains("\"refundOf\":\"o\""),
        )
    }

    // ---- validateExpense ----

    private fun assertInvalid(record: LocalExpense) {
        assertThrows("accepted $record", IllegalArgumentException::class.java) { validateExpense(record, known) }
    }

    @Test
    fun validate_refundOf_requiresRefund_notSelf_andIdShape() {
        validateExpense(local(isRefund = true, refundOf = "orig"), known)
        validateExpense(local(isRefund = true, refundOf = "a".repeat(64)), known)
        assertInvalid(local(isRefund = false, refundOf = "orig"))
        assertInvalid(local(id = "e1", isRefund = true, refundOf = "e1"))
        listOf("", "has space", "a/b", "a".repeat(65), "accént").forEach {
            assertInvalid(local(isRefund = true, refundOf = it))
        }
    }

    @Test
    fun validate_splitId_idShape_anyKind_andDanglingRefundAccepted() {
        validateExpense(local(splitId = "s-1_A"), known)
        validateExpense(local(isRefund = true, splitId = "s"), known)
        listOf("", "has space", "a/b", "a".repeat(65)).forEach {
            assertInvalid(local(splitId = it))
        }
        // The original does not need to exist: a link may dangle.
        validateExpense(local(isRefund = true, refundOf = "deleted-long-ago"), known)
    }

    @Test
    fun add_withLinks_persists_andRejectsInvalidLinksWithoutWriting() =
        runStoreTest {
            assertTrue(repo.addExpense(refund("r1", of = "missing")).isSuccess)
            assertEquals("missing", repo.getExpense("r1").getOrThrow()?.refundOf)

            val before = store.data.first()
            val notRefund = expense("x").copy(refundOf = "orig")
            assertTrue(repo.addExpense(notRefund).exceptionOrNull() is IllegalArgumentException)
            assertTrue(repo.addExpense(refund("self", of = "self")).exceptionOrNull() is IllegalArgumentException)
            assertEquals(before, store.data.first())
        }

    // ---- bulk writes: one store.update each ----

    @Test
    fun addKeepingIds_keepsIds_generatesBlank_inOneRevision() =
        runStoreTest {
            val siblings =
                listOf("s1", "", "s3").map { expense(it).copy(splitId = "grp") }

            val ids = repo.addExpensesKeepingIds(siblings).getOrThrow()

            assertEquals(listOf("s1", "id1", "s3"), ids)
            assertEquals(1L, diskDoc().revision)
            assertEquals(listOf("grp"), diskDoc().expenses.map { it.splitId }.distinct())
        }

    @Test
    fun addKeepingIds_oneInvalid_writesNothing() =
        runStoreTest {
            val result = repo.addExpensesKeepingIds(listOf(expense("a"), expense("b", amountCents = 0), expense("c")))

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertFalse(dataFile.exists())
        }

    @Test
    fun addKeepingIds_existingId_isReplaced_notDuplicated() =
        runStoreTest {
            repo.addExpense(expense("a", name = "old"))

            repo.addExpensesKeepingIds(listOf(expense("a", name = "new"), expense("b")))

            assertEquals(listOf("a", "b"), diskDoc().expenses.map { it.id }.sorted())
            assertEquals("new", diskDoc().expenses.single { it.id == "a" }.name)
        }

    @Test
    fun updateExpenses_appliesAll_inOneRevision() =
        runStoreTest {
            repo.addExpensesKeepingIds(listOf(expense("a"), expense("b"), expense("c")))

            val result = repo.updateExpenses(listOf(expense("a", name = "A2"), expense("c", name = "C2")))

            assertTrue(result.isSuccess)
            assertEquals(2L, diskDoc().revision)
            assertEquals(listOf("A2", "Lunch", "C2"), diskDoc().expenses.map { it.name })
        }

    @Test
    fun updateExpenses_missingOrInvalid_isAllOrNothing() =
        runStoreTest {
            repo.addExpensesKeepingIds(listOf(expense("a"), expense("b")))
            val before = store.data.first()

            assertTrue(repo.updateExpenses(listOf(expense("a", name = "A2"), expense("ghost"))).isFailure)
            assertTrue(repo.updateExpenses(listOf(expense("a", name = "A2"), expense("b", amountCents = -1))).isFailure)

            assertEquals(before, store.data.first())
        }

    @Test
    fun deleteExpenses_removesAll_inOneRevision_ignoringMissing() =
        runStoreTest {
            repo.addExpensesKeepingIds(listOf(expense("a"), expense("b"), expense("c")))

            assertTrue(repo.deleteExpenses(listOf("a", "zzz", "c")).isSuccess)

            assertEquals(listOf("b"), diskDoc().expenses.map { it.id })
            assertEquals(2L, diskDoc().revision)
        }

    // ---- observeLinkedRefunds ----

    @Test
    fun observeLinkedRefunds_onlyLinkedRefunds_includingDangling_andUpdatesLive() =
        runStoreTest {
            repo.addExpensesKeepingIds(
                listOf(
                    expense("o"),
                    refund("r1", of = "o"),
                    refund("r2", of = "gone"),
                    expense("plain").copy(splitId = "s"),
                ),
            )
            assertEquals(listOf("r1", "r2"), repo.observeLinkedRefunds().first().map { it.id })

            repo.deleteExpenses(listOf("o", "r1"))
            assertEquals(listOf("r2"), repo.observeLinkedRefunds().first().map { it.id })
        }

    @Test
    fun refundLinkedOrNot_neverChangesTheTotals() =
        runStoreTest {
            val original = expense("orig", amountCents = 10_000)
            repo.addExpensesKeepingIds(listOf(original, refund("r1", of = "orig", cents = 2_500)))
            val linked = repo.getAllExpenses().getOrThrow().netCents()

            repo.updateExpenses(listOf(refund("r1", of = null, cents = 2_500)))
            val unlinked = repo.getAllExpenses().getOrThrow().netCents()

            assertEquals(7_500L, linked)
            assertEquals(linked, unlinked)
        }

    // ---- guest file schema 24 -> 25 ----

    @Test
    fun v24File_loadsAtV25_keepsExpenses_isNotRewrittenOnRead() =
        runStoreTest {
            val v24 =
                """{"schemaVersion":24,"revision":3,"expenses":[{"id":"e1","name":"Lunch","amountCents":1000,""" +
                    """"categoryId":"FOOD","date":"$at"}]}"""
            dataFile.writeText(v24)

            val doc = newStore().data.first()

            assertEquals(26, doc.schemaVersion)
            assertEquals(3L, doc.revision)
            assertEquals(listOf(local()), doc.expenses)
            assertTrue(filesInDir().none { it.contains("corrupt") })
            assertEquals(v24, dataFile.readText())
        }

    @Test
    fun v24File_afterAWriteWithLinks_isStoredAsV25_andLinksSurviveAReload() =
        runStoreTest {
            dataFile.writeText("""{"schemaVersion":24,"revision":3}""")
            val repo = LocalExpenseRepository(newStore(), SequenceIds())

            assertTrue(repo.addExpensesKeepingIds(listOf(expense("o"), refund("r", of = "o"))).isSuccess)

            assertEquals(26, diskDoc().schemaVersion)
            val reloaded =
                newStore()
                    .data
                    .first()
                    .expenses
                    .associateBy { it.id }
            assertEquals("o", reloaded.getValue("r").refundOf)
            assertNull(reloaded.getValue("o").refundOf)
        }

    @Test
    fun v25File_withLinks_loadsUnchanged() =
        runStoreTest {
            val v25 =
                """{"schemaVersion":25,"revision":4,"expenses":[{"id":"r","name":"R","amountCents":5,""" +
                    """"categoryId":"FOOD","date":"$at",""" +
                    """"isRefund":true,"refundOf":"o","splitId":"s"}]}"""
            dataFile.writeText(v25)

            val e =
                newStore()
                    .data
                    .first()
                    .expenses
                    .single()

            assertEquals("o", e.refundOf)
            assertEquals("s", e.splitId)
        }
}

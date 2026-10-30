package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalExpense
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Export and guest restore keep refund/split links together with their record ids. */
class BackupRestoreLinksTest : BackupRepositoryTestBase() {
    private val at = "2026-03-10T12:00:00Z"
    private val linked =
        listOf(
            backupExpense("orig", amountCents = 5000),
            LocalExpense("ref", "Return", 2000, "FOOD", at, isRefund = true, refundOf = "orig"),
            LocalExpense("part", "Shop", 300, "FOOD", at, splitId = "grp"),
        )

    @Test
    fun restore_guest_keepsIdsAndLinks() =
        runStoreTest {
            assertTrue(repository().restoreAsGuest(backupDoc(expenses = linked)).isSuccess)

            val stored = localStore.data.first().expenses
            assertEquals(linked, stored)
            assertEquals(listOf("orig", "ref", "part"), stored.map { it.id })
            assertEquals("orig", stored.single { it.id == "ref" }.refundOf)
            assertEquals("grp", stored.single { it.id == "part" }.splitId)
        }

    @Test
    fun export_writesTheLinksOfTheRepositoryExpenses() =
        runStoreTest {
            val at = Instant.parse(EXPORTED_AT)
            val orig = testExpense("orig", 5000, at)
            val ref = testExpense("ref", 2000, at, isRefund = true).copy(refundOf = "orig", splitId = "grp")

            val doc = repository(expenses = listOf(orig, ref)).buildBackup(at).getOrThrow()

            assertEquals(listOf(null, "orig"), doc.expenses.map { it.refundOf })
            assertEquals(listOf(null, "grp"), doc.expenses.map { it.splitId })
            assertEquals(doc, BackupCodec.decode(BackupCodec.encode(doc)).success().document)
        }
}

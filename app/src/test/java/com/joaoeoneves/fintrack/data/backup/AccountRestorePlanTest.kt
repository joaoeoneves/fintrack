package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.migration.PendingWrite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRestorePlanTest {
    private fun write(
        collection: String,
        id: String,
    ) = PendingWrite(collection, id, emptyMap())

    private fun plan(
        writes: List<List<PendingWrite>> = emptyList(),
        server: Map<String, Set<String>> = emptyMap(),
        hasProfile: Boolean = false,
        hasAutomation: Boolean = false,
    ) = accountRestorePlanOf(writes, server, hasProfile, hasAutomation)

    private fun AccountRestorePlan.deletes() = deleteBatches.flatten().map { it.collection to it.id }

    @Test
    fun emptyBackup_emptyServer_isANoOp() {
        val plan = plan()

        assertTrue(plan.writeBatches.isEmpty())
        assertTrue(plan.deleteBatches.isEmpty())
        assertEquals(0, plan.total)
    }

    @Test
    fun oversizedWriteBatch_isSplitTo450() {
        val big = List(1_000) { write("expenses", "e$it") }

        val plan = plan(writes = listOf(big, listOf(write("income", "i1"))))

        assertEquals(listOf(450, 450, 100, 1), plan.writeBatches.map { it.size })
        assertEquals(1_001, plan.total)
        assertEquals(
            "e0",
            plan.writeBatches
                .first()
                .first()
                .id,
        )
        assertEquals("e999", plan.writeBatches[2].last().id)
    }

    @Test
    fun exactly450_staysOneBatch() {
        val plan = plan(writes = listOf(List(450) { write("expenses", "e$it") }))

        assertEquals(listOf(450), plan.writeBatches.map { it.size })
    }

    @Test
    fun deleteBatches_areAtMost450() {
        val server = mapOf("expenses" to List(1_000) { "x$it" }.toSet())

        val plan = plan(server = server)

        assertEquals(listOf(450, 450, 100), plan.deleteBatches.map { it.size })
        assertTrue(plan.writeBatches.isEmpty())
    }

    @Test
    fun deletes_onlyWhatTheBackupDoesNotHave() {
        val plan =
            plan(
                writes = listOf(listOf(write("expenses", "keep"))),
                server = mapOf("expenses" to setOf("keep", "stale")),
            )

        assertEquals(listOf("expenses" to "stale"), plan.deletes())
    }

    @Test
    fun sameIdInAnotherCollection_isNotKept() {
        val plan =
            plan(
                writes = listOf(listOf(write("income", "same"))),
                server = mapOf("expenses" to setOf("same")),
            )

        assertEquals(listOf("expenses" to "same"), plan.deletes())
    }

    @Test
    fun deletes_followTheReversePruneOrder_andUpsertsComeFirst() {
        val server = RESTORE_PRUNE_ORDER.associateWith { setOf("old") }

        val plan = plan(writes = listOf(listOf(write("categories", "c"))), server = server)

        assertEquals(RESTORE_PRUNE_ORDER, plan.deletes().map { it.first })
        assertEquals(
            listOf(
                "goals",
                "monthSettings",
                "recurring",
                "plannedIncome",
                "income",
                "expenses",
                "budgets",
                "categories",
                "categoryGroups",
            ),
            RESTORE_PRUNE_ORDER,
        )
        assertEquals(1, plan.writeBatches.size)
        assertEquals(RESTORE_PRUNE_ORDER.size, plan.deleteBatches.sumOf { it.size })
    }

    @Test
    fun deletesWithinACollection_areSortedById() {
        val plan = plan(server = mapOf("goals" to setOf("b", "c", "a")))

        assertEquals(listOf("a", "b", "c"), plan.deletes().map { it.second })
    }

    @Test
    fun singletons_deletedOnlyWhenAbsentFromTheBackupAndPresentOnTheServer() {
        val absent = plan(hasProfile = true, hasAutomation = true)
        assertEquals(listOf("plan" to "salaryProfile", "plan" to "automation"), absent.deletes())

        val onlyProfile = plan(hasProfile = true)
        assertEquals(listOf("plan" to "salaryProfile"), onlyProfile.deletes())

        val notOnServer = plan(writes = listOf(listOf(write("categories", "c"))))
        assertTrue(notOnServer.deleteBatches.isEmpty())
    }

    @Test
    fun singletons_keptWhenTheBackupWritesThem() {
        val plan =
            plan(
                writes = listOf(listOf(write("plan", "salaryProfile"), write("plan", "automation"))),
                hasProfile = true,
                hasAutomation = true,
            )

        assertTrue(plan.deleteBatches.isEmpty())
    }

    @Test
    fun singletons_comeAfterTheCollections() {
        val plan = plan(server = mapOf("goals" to setOf("g")), hasProfile = true)

        assertEquals(listOf("goals" to "g", "plan" to "salaryProfile"), plan.deletes())
    }

    @Test
    fun emptyBackup_deletesEverythingOnTheServer() {
        val server = RESTORE_PRUNE_ORDER.associateWith { setOf("a", "b") }

        val plan = plan(server = server, hasProfile = true, hasAutomation = true)

        assertTrue(plan.writeBatches.isEmpty())
        assertEquals(RESTORE_PRUNE_ORDER.size * 2 + 2, plan.total)
        assertEquals(plan.total, plan.deleteBatches.sumOf { it.size })
    }

    @Test
    fun collectionsOutsideThePruneList_areNeverDeleted() {
        val plan = plan(server = mapOf("somethingElse" to setOf("x")))

        assertTrue(plan.deleteBatches.isEmpty())
    }
}

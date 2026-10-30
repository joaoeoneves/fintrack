package com.joaoeoneves.fintrack.data.local

import android.app.Application
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/** Schema versioning of the guest file: older files upgrade on read, newer ones are set aside. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalDataStoreSchemaTest : LocalStoreTestBase() {
    private val v1File = """{"schemaVersion":1,"revision":7,"budgets":[{"categoryId":"FOOD","limitCents":5}]}"""

    private fun recurringJson(
        id: String,
        autoPost: String,
    ) = """{"id":"$id","type":"EXPENSE","name":"Rent","amountCents":5,"categoryId":"FOOD","frequency":"MONTHLY",""" +
        """"dayOfMonth":15,"startDate":"2020-01-01","active":true$autoPost}"""

    private val removedKeys =
        listOf(
            "budgets",
            "goals",
            "salaryPaysNextMonth",
            "workingDays",
            "fundedByLineId",
            "autoPost",
        )

    private val v22File =
        """{"schemaVersion":22,"revision":9,""" +
            """"budgets":[{"categoryId":"FOOD","limitCents":5}],""" +
            """"goals":[{"id":"g","name":"Trip","targetCents":100,"startDate":"2026-01-01"}],""" +
            """"categoryGroups":[{"id":"e1","name":"Food","order":0,"amountCents":900,"fundedByLineId":"meal"}],""" +
            """"automation":{"lineThrough":{"p1":"2026-09"},"salaryPaysNextMonth":true},""" +
            """"monthSettings":[{"month":"2026-09","workingDays":15},""" +
            """{"month":"2026-10","workingDays":15,"daysOff":2}],""" +
            """"recurring":[${recurringJson("manual", ",\"autoPost\":false")},""" +
            """${recurringJson("legacy", "")},${recurringJson("auto", ",\"autoPost\":true")}]}"""

    @Test
    fun currentSchemaVersion_isTwentyFour() {
        assertEquals(24, LOCAL_SCHEMA_VERSION)
        assertEquals(24, LocalDataDocument().schemaVersion)
    }

    @Test
    fun v1File_loadsWithEmptyPlannedIncome_dropsRemovedBudgets_andKeepsRevision() =
        runStoreTest {
            dataFile.writeText(v1File)

            val doc = newStore().data.first()

            assertEquals(emptyList<LocalPlannedIncome>(), doc.plannedIncome)
            assertEquals(7L, doc.revision)
            assertTrue(filesInDir().none { it.contains("corrupt") })
        }

    @Test
    fun v1File_isNotRewrittenOnRead() =
        runStoreTest {
            dataFile.writeText(v1File)

            newStore().data.first()

            assertEquals(v1File, dataFile.readText())
        }

    @Test
    fun v1File_isRewrittenAsCurrent_onNextWrite_withoutTheRemovedKeys() =
        runStoreTest {
            dataFile.writeText(v1File)
            val store = newStore()
            val line = LocalPlannedIncome("p", "Salary", 100L, 0)

            assertTrue(store.update { it.copy(plannedIncome = listOf(line)) to Unit }.isSuccess)

            val onDisk = diskDoc()
            assertEquals(LOCAL_SCHEMA_VERSION, onDisk.schemaVersion)
            assertEquals(8L, onDisk.revision)
            assertEquals(listOf(line), onDisk.plannedIncome)
            assertFalse(dataFile.readText().contains("budgets"))
        }

    @Test
    fun v22File_loadsUpgraded_notMovedAside_removedDataDropped() =
        runStoreTest {
            dataFile.writeText(v22File)

            val doc = newStore().data.first()

            assertEquals(LOCAL_SCHEMA_VERSION, doc.schemaVersion)
            assertEquals(9L, doc.revision)
            assertTrue(filesInDir().none { it.contains("corrupt") })
            assertEquals(listOf(LocalCategoryGroup("e1", "Food", 0)), doc.categoryGroups)
            assertEquals(LocalAutomation(mapOf("p1" to "2026-09")), doc.automation)
            // The record that only carried the removed manual working days is gone; the days-off one stays.
            assertEquals(listOf(LocalMonthSettings("2026-10", 2)), doc.monthSettings)
        }

    @Test
    fun v22File_manualRecurringItems_haveTheirBacklogHandled_autoPostOnesUntouched() =
        runStoreTest {
            dataFile.writeText(v22File)

            val byId =
                newStore()
                    .data
                    .first()
                    .recurring
                    .associateBy { it.id }

            val today = LocalDate.now()
            listOf("manual", "legacy").forEach { id ->
                val cursor = LocalDate.parse(byId.getValue(id).handledThrough!!)
                assertTrue("$id cursor $cursor must be before today", cursor.isBefore(today))
                assertTrue("$id cursor must be recent", cursor.isAfter(today.minusMonths(2)))
            }
            assertNull(byId.getValue("auto").handledThrough)
            byId.values.forEach { assertNull(it.autoPost) }
        }

    @Test
    fun v22File_afterTheNextWrite_hasNoRemovedKeysOnDisk() =
        runStoreTest {
            dataFile.writeText(v22File)
            val store = newStore()

            assertTrue(store.update { it.copy(dismissedRecurringSuggestions = listOf("x")) to Unit }.isSuccess)

            val text = dataFile.readText()
            removedKeys.forEach { assertFalse("$it must not be written", text.contains("\"$it\"")) }
            assertEquals(LOCAL_SCHEMA_VERSION, diskDoc().schemaVersion)
        }

    @Test
    fun nextSchemaVersion_isMovedAside_andStoreStartsEmpty() =
        runStoreTest {
            val content = """{"schemaVersion":${LOCAL_SCHEMA_VERSION + 1},"revision":5,"plannedIncome":[]}"""
            dataFile.writeText(content)

            assertEquals(LocalDataDocument(), newStore().data.first())

            val aside = filesInDir().filter { it.startsWith("guest-data.corrupt-") }
            assertEquals(1, aside.size)
            assertEquals(content, File(tmp.root, aside.single()).readText())
            assertFalse(dataFile.exists())
        }

    @Test
    fun upgradedTo23_isPure_movesManualCursors_leavesAutoPostOnesAlone() {
        val today = day(2026, 3, 20)
        val manual = recurringItem(id = "m", handledThrough = day(2026, 1, 15)).toLocal().copy(autoPost = false)
        val auto = recurringItem(id = "a", handledThrough = day(2026, 1, 15)).toLocal().copy(autoPost = true)
        val doc = LocalDataDocument(schemaVersion = 22, recurring = listOf(manual, auto))

        val once = doc.upgradedTo23(today)

        assertEquals(23, once.schemaVersion)
        assertEquals("2026-03-15", once.recurring.single { it.id == "m" }.handledThrough)
        assertEquals("2026-01-15", once.recurring.single { it.id == "a" }.handledThrough)
        assertTrue(once.recurring.all { it.autoPost == null })
    }

    @Test
    fun upgradedTo23_todaysOccurrenceStaysPending() {
        val manual = recurringItem(id = "m", handledThrough = day(2026, 1, 15)).toLocal().copy(autoPost = false)

        val upgraded = LocalDataDocument(recurring = listOf(manual)).upgradedTo23(day(2026, 3, 15))

        assertEquals("2026-02-15", upgraded.recurring.single().handledThrough)
    }

    @Test
    fun v23File_legacyDaysOffBecomesVacation_andNoLegacyKeyRemainsAfterAWrite() =
        runStoreTest {
            dataFile.writeText(
                """{"schemaVersion":23,"revision":4,"monthSettings":[{"month":"2026-10","daysOff":3},""" +
                    """{"month":"2026-11","vacationDays":1,"sickDays":2}]}""",
            )

            val store = newStore()
            val doc = store.data.first()

            assertEquals(LOCAL_SCHEMA_VERSION, doc.schemaVersion)
            assertEquals(
                listOf(LocalMonthSettings("2026-10", vacationDays = 3), LocalMonthSettings("2026-11", 1, 2)),
                doc.monthSettings,
            )
            val dec = LocalMonthSettings("2026-12", sickDays = 1)
            store.update { it.copy(monthSettings = it.monthSettings + dec) to Unit }
            assertFalse(dataFile.readText().contains("daysOff"))
        }

    @Test
    fun upgradedTo24_isIdempotent_dropsEmptyRecords_andDoesNotRerunUpgradedTo23() {
        val legacy = LocalMonthSettings("2026-10", legacyDaysOff = 2)
        val doc = LocalDataDocument(schemaVersion = 23, monthSettings = listOf(legacy, LocalMonthSettings("2026-09")))

        val once = doc.upgradedTo24()
        val twice = once.upgradedTo24()

        assertEquals(24, once.schemaVersion)
        assertEquals(listOf(LocalMonthSettings("2026-10", vacationDays = 2)), once.monthSettings)
        assertEquals(once, twice)
    }

    @Test
    fun upgradedTo24_addsLegacyToExistingVacation() {
        val record = LocalMonthSettings("2026-10", vacationDays = 1, legacyDaysOff = 2)

        assertEquals(listOf(LocalMonthSettings("2026-10", vacationDays = 3)), listOf(record).upgradedTo24())
    }
}

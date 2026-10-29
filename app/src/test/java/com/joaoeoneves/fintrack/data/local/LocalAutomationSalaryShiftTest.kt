package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAutomationSalaryShiftTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalAutomationSettingsRepository(store) }

    @Test
    fun setSalaryPaysNextMonth_createsTheRecord() =
        runStoreTest {
            assertTrue(repo.setSalaryPaysNextMonth(true).isSuccess)

            assertEquals(true, repo.observeAutomationSettings().first().salaryPaysNextMonth)
            assertEquals(true, diskDoc().automation?.salaryPaysNextMonth)
        }

    @Test
    fun setSalaryPaysNextMonth_preservesLineThrough() =
        runStoreTest {
            store.update { it.copy(automation = LocalAutomation(lineThrough = mapOf("a" to "2026-09"))) to Unit }

            repo.setSalaryPaysNextMonth(false)

            val automation = diskDoc().automation
            assertEquals(mapOf("a" to "2026-09"), automation?.lineThrough)
            assertEquals(false, automation?.salaryPaysNextMonth)
        }

    @Test
    fun v17File_withoutTheField_decodesToDefault() =
        runStoreTest {
            dataFile.writeText("""{"schemaVersion":17,"revision":1,"automation":{"paydayEnabled":true}}""")

            val settings = repo.observeAutomationSettings().first()

            assertNull(settings.salaryPaysNextMonth)
            assertEquals(AutomationSettings(legacyPaydayEnabled = true), settings)
        }

    @Test
    fun v18File_roundTrips() =
        runStoreTest {
            dataFile.writeText("""{"schemaVersion":18,"revision":1,"automation":{"salaryPaysNextMonth":false}}""")

            assertEquals(false, repo.observeAutomationSettings().first().salaryPaysNextMonth)
        }
}

package com.joaoeoneves.fintrack.data.preferences

import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountPlanSettingsRepositoryTest : PreferencesStoreTestBase() {
    private val store = UserPreferencesStore(newDataStore(newScope()))

    private fun repo(account: Boolean?) =
        FakeAutomationSettingsRepository(AutomationSettings(salaryPaysNextMonth = account)).let {
            it to AccountPlanSettingsRepository(it, store)
        }

    private suspend fun legacy() = store.data.first().salaryPaysNextMonth

    @Test
    fun legacyTrue_accountNull_emitsTrue_andWritesAccount() =
        runTest {
            store.update { it.copy(salaryPaysNextMonth = true) }
            val (fake, plan) = repo(null)

            assertEquals(true, plan.observeSalaryPaysNextMonth().first())

            assertEquals(true, fake.settings.salaryPaysNextMonth)
        }

    @Test
    fun accountTrue_legacySet_clearsTheStore() =
        runTest {
            store.update { it.copy(salaryPaysNextMonth = false) }
            val (_, plan) = repo(true)

            assertEquals(true, plan.observeSalaryPaysNextMonth().first())

            assertNull(legacy())
        }

    @Test
    fun accountFalse_legacyTrue_accountWins_andLegacyCleared() =
        runTest {
            store.update { it.copy(salaryPaysNextMonth = true) }
            val (fake, plan) = repo(false)

            assertEquals(false, plan.observeSalaryPaysNextMonth().first())

            assertNull(legacy())
            assertEquals(false, fake.settings.salaryPaysNextMonth)
        }

    @Test
    fun bothNull_emitsNull_andWritesNothing() =
        runTest {
            val (fake, plan) = repo(null)

            assertNull(plan.observeSalaryPaysNextMonth().first())

            assertNull(fake.settings.salaryPaysNextMonth)
            assertNull(legacy())
        }

    @Test
    fun set_writesAccount_andClearsLegacy() =
        runTest {
            store.update { it.copy(salaryPaysNextMonth = true) }
            val (fake, plan) = repo(true)

            plan.setSalaryPaysNextMonth(false)

            assertEquals(false, fake.settings.salaryPaysNextMonth)
            assertNull(legacy())
            assertEquals(false, plan.observeSalaryPaysNextMonth().first())
        }
}

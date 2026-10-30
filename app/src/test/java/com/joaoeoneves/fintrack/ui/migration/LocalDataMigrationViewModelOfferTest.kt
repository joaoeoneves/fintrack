package com.joaoeoneves.fintrack.ui.migration

import com.joaoeoneves.fintrack.data.backup.backupRecurring
import com.joaoeoneves.fintrack.data.local.LocalIncome
import com.joaoeoneves.fintrack.data.local.LocalPlannedIncome
import com.joaoeoneves.fintrack.domain.model.Session
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataMigrationViewModelOfferTest : LocalDataMigrationViewModelTestBase() {
    @Test
    fun signedInWithLocalData_showsOfferWithCounts() =
        runStoreTest {
            seedLocal(expenses = 2, income = 1)
            val vm = viewModel()
            settle()

            assertEquals(MigrationUiState.Offer(counts(expenses = 2, income = 1)), vm.uiState.value)
        }

    @Test
    fun onlyPlannedIncome_showsOfferWithPlannedIncomeCount() =
        runStoreTest {
            store.update { it.copy(plannedIncome = listOf(LocalPlannedIncome("p1", "Pay", 100, 0))) to Unit }
            val vm = viewModel()
            settle()

            assertEquals(MigrationUiState.Offer(counts(expenses = 0, plannedIncome = 1)), vm.uiState.value)
        }

    @Test
    fun onlyRecurring_showsOfferWithRecurringCount() =
        runStoreTest {
            store.update { it.copy(recurring = listOf(backupRecurring("r1"), backupRecurring("r2"))) to Unit }
            val vm = viewModel()
            settle()

            assertEquals(MigrationUiState.Offer(counts(expenses = 0, recurring = 2)), vm.uiState.value)
        }

    @Test
    fun emptyLocalData_staysHidden() =
        runStoreTest {
            val vm = viewModel()
            settle()

            assertEquals(MigrationUiState.Hidden, vm.uiState.value)
        }

    @Test
    fun guestOrSignedOut_staysHidden() =
        runStoreTest {
            seedLocal()
            val vm = viewModel()
            sessions.emit(Session.Guest)
            settle()
            assertEquals(MigrationUiState.Hidden, vm.uiState.value)

            sessions.emit(Session.SignedOut)
            settle()
            assertEquals(MigrationUiState.Hidden, vm.uiState.value)
        }

    @Test
    fun signingInLater_showsOffer() =
        runStoreTest {
            sessions.emit(Session.Guest)
            seedLocal()
            val vm = viewModel()
            settle()
            assertEquals(MigrationUiState.Hidden, vm.uiState.value)

            sessions.emit(Session.SignedIn(user))
            settle()

            assertTrue(vm.uiState.value is MigrationUiState.Offer)
        }

    @Test
    fun alreadyHandledRevision_staysHidden() =
        runStoreTest {
            val doc = seedLocal()
            offerStore.markHandled(doc.revision)
            val vm = viewModel()
            settle()

            assertEquals(MigrationUiState.Hidden, vm.uiState.value)
        }

    @Test
    fun handledOlderRevision_offersAgain() =
        runStoreTest {
            val doc = seedLocal()
            offerStore.markHandled(doc.revision - 1)
            val vm = viewModel()
            settle()

            assertTrue(vm.uiState.value is MigrationUiState.Offer)
        }

    @Test
    fun decline_hidesRecordsRevisionAndKeepsLocalData() =
        runStoreTest {
            val doc = seedLocal()
            val vm = viewModel()
            settle()

            vm.onDecline()
            settle()

            assertEquals(MigrationUiState.Hidden, vm.uiState.value)
            assertEquals(doc.revision, offerStore.handledRevision.first())
            assertEquals(doc, store.data.first())
            assertTrue(migrator.received.isEmpty())
        }

    @Test
    fun dismiss_hidesWithoutWritingAndIsNotReofferedForSameRevision() =
        runStoreTest {
            seedLocal()
            val vm = viewModel()
            settle()

            vm.onDismiss()
            settle()
            sessions.emit(Session.Guest)
            sessions.emit(Session.SignedIn(user))
            settle()

            assertEquals(MigrationUiState.Hidden, vm.uiState.value)
            assertNull(offerStore.handledRevision.first())
        }

    @Test
    fun dismiss_thenLocalDataChanges_offersNewRevision() =
        runStoreTest {
            seedLocal()
            val vm = viewModel()
            settle()
            vm.onDismiss()
            settle()

            store.update { it.copy(income = it.income + LocalIncome("i9", "Bonus", 5, "2026-03-11T12:00:00Z")) to Unit }
            settle()

            assertEquals(MigrationUiState.Offer(counts(expenses = 2, income = 2)), vm.uiState.value)
        }

    @Test
    fun offerFollowsLocalDataWhileShown() =
        runStoreTest {
            seedLocal(expenses = 1)
            val vm = viewModel()
            settle()

            store.update { it.copy(expenses = emptyList(), income = emptyList()) to Unit }
            settle()

            assertEquals(MigrationUiState.Hidden, vm.uiState.value)
        }
}

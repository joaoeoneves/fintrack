package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.Session
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationOfferPolicyTest {
    private val signedIn = Session.SignedIn(AuthUser("u", null, null, null))
    private val data = localDoc(expenses = listOf(localExpense("e1")), revision = 3)

    @Test
    fun signedInWithDataNeverHandled_offers() {
        assertTrue(shouldOfferMigration(signedIn, data, null))
    }

    @Test
    fun otherSessions_doNotOffer() {
        listOf(Session.Loading, Session.SignedOut, Session.Guest).forEach {
            assertFalse("$it", shouldOfferMigration(it, data, null))
        }
    }

    @Test
    fun emptyLocal_doesNotOffer() {
        assertFalse(shouldOfferMigration(signedIn, localDoc(revision = 3), null))
    }

    @Test
    fun onlyPlannedIncome_offers() {
        val onlyPlan = localDoc(revision = 3).copy(plannedIncome = listOf(localPlanned("p1")))

        assertFalse(onlyPlan.isEmpty)
        assertTrue(shouldOfferMigration(signedIn, onlyPlan, null))
    }

    @Test
    fun onlyRecurring_offers() {
        val onlyRecurring = localDoc(revision = 3, recurring = listOf(localRecurring("r1")))

        assertFalse(onlyRecurring.isEmpty)
        assertTrue(shouldOfferMigration(signedIn, onlyRecurring, null))
    }

    @Test
    fun sameRevisionHandled_doesNotOffer() {
        assertFalse(shouldOfferMigration(signedIn, data, 3L))
    }

    @Test
    fun differentRevisionHandled_offersAgain() {
        assertTrue(shouldOfferMigration(signedIn, data, 2L))
        assertTrue(shouldOfferMigration(signedIn, data, 4L))
    }

    @Test
    fun revisionZeroDataNeverHandled_offers() {
        assertTrue(shouldOfferMigration(signedIn, data.copy(revision = 0), null))
        assertFalse(shouldOfferMigration(signedIn, data.copy(revision = 0), 0L))
    }
}

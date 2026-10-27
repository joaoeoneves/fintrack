package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.TestDocumentSnapshots
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Out-of-range Long `order` values are clamped to the Int range instead of silently overflowing. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlannedIncomeOrderClampTest {
    private lateinit var firestore: FirebaseFirestore

    @Before
    fun setUp() {
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        firestore = FirebaseFirestore.getInstance(app)
    }

    private fun orderOf(order: Long): Int? {
        val fields =
            mapOf(
                "label" to TestDocumentSnapshots.stringValue("Pay"),
                "amountCents" to TestDocumentSnapshots.longValue(100L),
                "order" to TestDocumentSnapshots.longValue(order),
            )
        val snapshot = TestDocumentSnapshots.found(firestore, "p1", fields, collection = "plannedIncome")
        val m =
            Class
                .forName("com.joaoeoneves.fintrack.data.FirestorePlannedIncomeRepositoryKt")
                .getDeclaredMethod("toPlannedIncomeOrNull", DocumentSnapshot::class.java)
        m.isAccessible = true
        return (m.invoke(null, snapshot) as com.joaoeoneves.fintrack.domain.model.PlannedIncome?)?.order
    }

    @Test
    fun orderAboveIntMax_clampsToIntMax() {
        assertEquals(Int.MAX_VALUE, orderOf(Int.MAX_VALUE.toLong() + 1))
        assertEquals(Int.MAX_VALUE, orderOf(Long.MAX_VALUE))
    }

    @Test
    fun orderBelowIntMin_clampsToIntMin() {
        assertEquals(Int.MIN_VALUE, orderOf(Int.MIN_VALUE.toLong() - 1))
    }

    @Test
    fun orderWithinRange_isUnchanged() {
        assertEquals(Int.MAX_VALUE, orderOf(Int.MAX_VALUE.toLong()))
        assertEquals(0, orderOf(0L))
    }
}

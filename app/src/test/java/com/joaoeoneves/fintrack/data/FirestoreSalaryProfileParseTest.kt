package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.TestDocumentSnapshots
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Parsing a stored salary profile document: a missing `extras` field is an empty list, never a legacy fallback. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirestoreSalaryProfileParseTest {
    private fun parse(vararg fields: Pair<String, com.google.firestore.v1.Value>): SalaryProfile? {
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        val snapshot: DocumentSnapshot =
            TestDocumentSnapshots.found(
                FirebaseFirestore.getInstance(app),
                "salaryProfile",
                mapOf(*fields),
                collection = "plan",
            )
        val method =
            Class
                .forName("com.joaoeoneves.fintrack.data.FirestoreSalaryProfileRepositoryKt")
                .getDeclaredMethod("parseSalaryProfile", DocumentSnapshot::class.java)
        method.isAccessible = true
        return method.invoke(null, snapshot) as SalaryProfile?
    }

    @Test
    fun profileWithoutExtras_hasAnEmptyExtrasList() {
        val profile =
            parse(
                "inputMode" to TestDocumentSnapshots.stringValue("GROSS"),
                "baseGrossCents" to TestDocumentSnapshots.longValue(100_000),
            )

        assertNotNull(profile)
        assertEquals(emptyList<Any>(), profile?.extras)
        assertEquals(100_000L, profile?.baseGrossCents)
    }

    @Test
    fun oldFlexAndBonusFields_areIgnored_notMigratedToExtras() {
        val profile =
            parse(
                "inputMode" to TestDocumentSnapshots.stringValue("GROSS"),
                "baseGrossCents" to TestDocumentSnapshots.longValue(100_000),
                "flexPpm" to TestDocumentSnapshots.longValue(70_000),
                "bonusCents" to TestDocumentSnapshots.longValue(10_000),
            )

        assertEquals(emptyList<Any>(), profile?.extras)
    }

    @Test
    fun raiseInFebruary_trueIsRead_absentIsFalse() {
        val base = "inputMode" to TestDocumentSnapshots.stringValue("GROSS")

        assertEquals(true, parse(base, "raiseInFebruary" to TestDocumentSnapshots.booleanValue(true))?.raiseInFebruary)
        assertEquals(false, parse(base)?.raiseInFebruary)
    }
}

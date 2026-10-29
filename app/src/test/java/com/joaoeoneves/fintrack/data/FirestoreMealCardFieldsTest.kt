package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.TestDocumentSnapshots
import com.google.firestore.v1.Value
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.EmploymentSector
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.MealPayment
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/** The 5.2 Firestore fields: written only when not the default (the two meal-card flags always), defaults on read. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirestoreMealCardFieldsTest {
    private lateinit var firestore: FirebaseFirestore

    @Before
    fun setUp() {
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        firestore = FirebaseFirestore.getInstance(app)
    }

    private fun str(value: String) = TestDocumentSnapshots.stringValue(value)

    private fun long(value: Long) = TestDocumentSnapshots.longValue(value)

    private fun bool(value: Boolean) = TestDocumentSnapshots.booleanValue(value)

    private fun parse(
        facade: String,
        function: String,
        collection: String,
        id: String,
        fields: Map<String, Value>,
    ): Any? {
        val snapshot = TestDocumentSnapshots.found(firestore, id, fields, collection = collection)
        val method =
            Class
                .forName("com.joaoeoneves.fintrack.data.$facade")
                .getDeclaredMethod(function, DocumentSnapshot::class.java)
        method.isAccessible = true
        return method.invoke(null, snapshot)
    }

    private fun parseProfile(vararg extra: Pair<String, Value>) =
        parse(
            "FirestoreSalaryProfileRepositoryKt",
            "parseSalaryProfile",
            "plan",
            "salaryProfile",
            mapOf("inputMode" to str("GROSS"), "baseGrossCents" to long(100_000), *extra),
        ) as SalaryProfile

    private fun parseLine(vararg extra: Pair<String, Value>) =
        parse(
            "FirestorePlannedIncomeRepositoryKt",
            "toPlannedIncomeOrNull",
            "plannedIncome",
            "p1",
            mapOf("label" to str("Pay"), "amountCents" to long(1_000), "order" to long(0), *extra),
        ) as PlannedIncome

    private fun parseExpense(vararg extra: Pair<String, Value>) =
        parse(
            "FirestoreExpenseRepositoryKt",
            "toExpenseOrNull",
            "expenses",
            "e1",
            mapOf(
                "name" to str("Lunch"),
                "amountCents" to long(500),
                "category" to str("FOOD"),
                "date" to TestDocumentSnapshots.timestampValue(Timestamp(Instant.parse("2026-10-01T12:00:00Z"))),
                *extra,
            ),
        ) as Expense

    // --- salary profile ---

    @Test
    fun profile_defaults_writeNoNewKeys() {
        val map = SalaryProfile(baseGrossCents = 100_000).toFirestoreMap()

        assertFalse(map.containsKey("sector"))
        assertFalse(map.containsKey("mealPayment"))
        assertFalse(map.containsKey("otherDeductionsPpm"))
    }

    @Test
    fun profile_nonDefaults_areWritten() {
        val map =
            SalaryProfile(
                baseGrossCents = 100_000,
                sector = EmploymentSector.PUBLIC,
                mealPayment = MealPayment.WITH_SALARY,
                otherDeductionsPpm = 35_000,
            ).toFirestoreMap()

        assertEquals("PUBLIC", map["sector"])
        assertEquals("WITH_SALARY", map["mealPayment"])
        assertEquals(35_000, map["otherDeductionsPpm"])
    }

    @Test
    fun profile_explicitPrivateSector_isWrittenAsPrivate() {
        assertEquals("PRIVATE", SalaryProfile(sector = EmploymentSector.PRIVATE).toFirestoreMap()["sector"])
    }

    @Test
    fun profile_absentFields_giveDefaults() {
        val profile = parseProfile()

        assertNull(profile.sector)
        assertEquals(MealPayment.CARD, profile.mealPayment)
        assertEquals(0, profile.otherDeductionsPpm)
    }

    @Test
    fun profile_values_parse() {
        val profile =
            parseProfile(
                "sector" to str("PUBLIC"),
                "mealPayment" to str("WITH_SALARY"),
                "otherDeductionsPpm" to long(35_000),
            )

        assertEquals(EmploymentSector.PUBLIC, profile.sector)
        assertEquals(MealPayment.WITH_SALARY, profile.mealPayment)
        assertEquals(35_000, profile.otherDeductionsPpm)
    }

    @Test
    fun profile_unknownEnums_giveDefaults() {
        val profile = parseProfile("sector" to str("FREELANCE"), "mealPayment" to str("CASH"))

        assertNull(profile.sector)
        assertEquals(MealPayment.CARD, profile.mealPayment)
    }

    // --- planned income ---

    @Test
    fun line_flag_writtenOnlyWhenTrue() {
        val off = PlannedIncome("p", "Pay", 1_000, 0, payDay = 5).toFirestoreMap()
        val on = PlannedIncome("p", "Pay", 1_000, 0, payDay = 5, payDayEarlierIfClosed = true).toFirestoreMap()

        assertFalse(off.containsKey("payDayEarlierIfClosed"))
        assertEquals(true, on["payDayEarlierIfClosed"])
    }

    @Test
    fun line_flag_parsesAndDefaultsToFalse() {
        assertFalse(parseLine().payDayEarlierIfClosed)
        assertTrue(parseLine("payDay" to long(1), "payDayEarlierIfClosed" to bool(true)).payDayEarlierIfClosed)
    }

    // --- expense ---

    private fun expense(card: Boolean) =
        Expense(
            "e1",
            "Lunch",
            500,
            BuiltInCategories.FOOD,
            Instant.parse("2026-10-01T12:00:00Z"),
            null,
            paidWithMealCard = card,
        )

    @Suppress("UNCHECKED_CAST")
    private fun expenseMap(expense: Expense): Map<String, Any?> {
        val method =
            Class
                .forName("com.joaoeoneves.fintrack.data.FirestoreExpenseRepositoryKt")
                .getDeclaredMethod("toFirestoreMap", Expense::class.java, Boolean::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(null, expense, false) as Map<String, Any?>
    }

    @Test
    fun expense_flag_isAlwaysWritten() {
        assertEquals(false, expenseMap(expense(false))["paidWithMealCard"])
        assertEquals(true, expenseMap(expense(true))["paidWithMealCard"])
    }

    @Test
    fun expense_flag_parsesAndDefaultsToFalse() {
        assertFalse(parseExpense().paidWithMealCard)
        assertTrue(parseExpense("paidWithMealCard" to bool(true)).paidWithMealCard)
        assertFalse(parseExpense("paidWithMealCard" to str("true")).paidWithMealCard)
    }

    // --- category ---

    @Test
    fun category_flag_isAlwaysWritten() {
        assertEquals(false, userCategory("c").toFirestoreMap()["mealCardByDefault"])
        assertEquals(true, userCategory("c").copy(mealCardByDefault = true).toFirestoreMap()["mealCardByDefault"])
    }

    @Test
    fun category_flag_parsesAndDefaultsToFalse() {
        fun parsed(vararg extra: Pair<String, Value>) =
            TestDocumentSnapshots
                .found(firestore, "abc", mapOf("name" to str("Gym"), *extra), collection = "categories")
                .toCategoryOrNull()

        assertEquals(false, parsed()?.mealCardByDefault)
        assertEquals(true, parsed("mealCardByDefault" to bool(true))?.mealCardByDefault)
    }
}

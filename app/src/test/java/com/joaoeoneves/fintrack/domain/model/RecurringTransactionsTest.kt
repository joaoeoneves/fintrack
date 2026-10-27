package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId

/** [transactionFor] and [occurrenceTransactionId]. */
class RecurringTransactionsTest {
    private val utc = ZoneId.of("UTC")
    private val catalog = BuiltInCategories.all
    private val occurrence = day(2026, 3, 15)

    @Test
    fun id_isRecUnderscoreItemUnderscoreCompactDate() {
        assertEquals("rec_abc_20260305", occurrenceTransactionId("abc", day(2026, 3, 5)))
    }

    @Test
    fun expense_carriesItemFieldsAndResolvedCategory() {
        val item = recurringItem(id = "r9", name = "Gym", amountCents = 2_500, note = "monthly")

        val tx = item.transactionFor(occurrence, utc, catalog) as Expense

        assertEquals("rec_r9_20260315", tx.id)
        assertEquals("Gym", tx.name)
        assertEquals(2_500L, tx.amountCents)
        assertEquals(BuiltInCategories.FOOD, tx.category)
        assertEquals("monthly", tx.note)
    }

    @Test
    fun expense_customCategoryIsResolvedFromTheCatalog() {
        val gym = userCategory("gym")
        val item = recurringItem(categoryId = gym.id)

        val tx = item.transactionFor(occurrence, utc, catalog + gym) as Expense

        assertEquals(gym, tx.category)
    }

    @Test
    fun expense_unknownCategory_becomesUnknownPlaceholder() {
        val item = recurringItem(categoryId = CategoryId("deleted"))

        val tx = item.transactionFor(occurrence, utc, catalog) as Expense

        assertEquals(Category.unknown(CategoryId("deleted")), tx.category)
        assertTrue(tx.category.isUnknown)
    }

    @Test
    fun income_usesTheNameAsSource() {
        val item = recurringItem(type = RecurringType.INCOME, name = "Salary", amountCents = 300_000)

        val tx = item.transactionFor(occurrence, utc, catalog) as Income

        assertEquals("rec_r1_20260315", tx.id)
        assertEquals("Salary", tx.source)
        assertEquals(300_000L, tx.amountCents)
        assertNull(tx.note)
    }

    @Test
    fun expenseWithoutCategory_isRejected() {
        val item = recurringItem(categoryId = null)

        assertThrows(IllegalArgumentException::class.java) { item.transactionFor(occurrence, utc, catalog) }
    }

    @Test
    fun date_isNoonInTheGivenZone() {
        val zone = ZoneId.of("Europe/Lisbon")

        val tx = recurringItem().transactionFor(occurrence, zone, catalog)

        val local = tx.date.atZone(zone)
        assertEquals(occurrence, local.toLocalDate())
        assertEquals(LocalTime.NOON, local.toLocalTime())
    }

    @Test
    fun date_staysOnTheSameLocalDayInExtremeZones() {
        listOf("America/Los_Angeles", "Pacific/Kiritimati", "Pacific/Pago_Pago").forEach { id ->
            val zone = ZoneId.of(id)
            val local = recurringItem().transactionFor(occurrence, zone, catalog).date.atZone(zone)

            assertEquals(id, occurrence, local.toLocalDate())
            assertEquals(id, LocalTime.NOON, local.toLocalTime())
        }
    }

    @Test
    fun leapDayOccurrence_hasAValidId() {
        assertEquals("rec_r1_20280229", occurrenceTransactionId("r1", day(2028, 2, 29)))
    }
}

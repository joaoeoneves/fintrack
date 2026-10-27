package com.joaoeoneves.fintrack.parity

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Earmark
import com.joaoeoneves.fintrack.domain.model.FLEX_PAYMENTS
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.PPM_SCALE
import com.joaoeoneves.fintrack.domain.model.PlanMonth
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.amountIn
import com.joaoeoneves.fintrack.domain.model.potsOf
import com.joaoeoneves.fintrack.domain.model.salaryBreakdownOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

/**
 * Parity with the owner's private spreadsheet. The figures live in `src/test/resources/private/spreadsheet.json`
 * (gitignored, never committed), so every number here comes from that file and the whole class is skipped when it
 * is absent, as on CI.
 */
class SpreadsheetParityTest {
    private lateinit var data: JsonObject

    @Before
    fun loadSpreadsheet() {
        val stream = javaClass.classLoader?.getResourceAsStream(RESOURCE)
        assumeTrue("private spreadsheet.json not present; parity test skipped", stream != null)
        data = Json.parseToJsonElement(stream!!.use { it.readBytes().decodeToString() }).jsonObject
    }

    private fun long(
        obj: JsonObject,
        key: String,
    ) = obj
        .getValue(key)
        .jsonPrimitive.content
        .toLong()

    private fun long(key: String) = long(data, key)

    private fun section(key: String) = data.getValue(key).jsonObject

    private fun longs(key: String) = data.getValue(key).jsonArray.map { it.jsonPrimitive.content.toLong() }

    private fun spendingCategory(
        id: String,
        groupId: String? = null,
    ) = Category(
        id = CategoryId(id),
        customName = id,
        iconKey = "icon",
        colorKey = "slate",
        kind = CategoryKind.SPENDING,
        order = 0,
        archived = false,
        builtIn = false,
        groupId = groupId,
    )

    private val month = YearMonth.of(2026, 10)

    private fun mealLine(earmark: Earmark? = null) =
        PlannedIncome(
            id = "meal",
            label = "Meal allowance",
            amountCents = long("mealRateCents"),
            order = 1,
            basis = IncomeBasis.PER_WORKING_DAY,
            earmark = earmark,
        )

    @Test
    fun meal_perWorkingDayLine_matchesSpreadsheetMonthlyTotal() {
        val days = long("mealDaysPerMonth").toInt()

        assertEquals(long("expectedMealMonthlyCents"), mealLine().amountIn(month, days))
    }

    @Test
    fun pots_foodPotAndBase_matchSpreadsheet() {
        val food = section("food")
        val group = CategoryGroup("g-food", "Food", 0)
        val dinners = spendingCategory("dinners", group.id)
        val snacks = spendingCategory("snacks", group.id)
        val recurring = spendingCategory("recurring")
        val investments = spendingCategory("investments")
        val investmentTotal = section("investmentsMonthlyCents").values.sumOf { it.jsonPrimitive.content.toLong() }
        val buckets =
            listOf(
                CategoryBudget(dinners, long(food, "dinnersMonthlyCents"), 0L),
                CategoryBudget(snacks, long(food, "snacksMonthlyCents"), 0L),
                CategoryBudget(recurring, longs("recurringMonthlyCents").sum(), 0L),
                CategoryBudget(investments, investmentTotal, 0L),
            )
        val salary =
            PlannedIncome("salary", "Salary", long(section("salary2026"), "expectedNetMonthlyCents"), 0)
        val meal = mealLine(earmark = Earmark.Group(group.id))

        val summary =
            potsOf(
                buckets = buckets,
                groups = listOf(group),
                plannedIncome = listOf(salary, meal),
                monthIncome = emptyList(),
                planMonth = PlanMonth(month, long("mealDaysPerMonth").toInt()),
            )

        val expected = section("expected")
        assertEquals(
            group.id,
            summary.pots
                .single()
                .target
                ?.id,
        )
        assertEquals(long(expected, "foodPotFreeCents"), summary.pots.single().freeCents)
        assertEquals(long(expected, "baseFreeCents"), summary.base.freeCents)
    }

    private fun int(
        obj: JsonObject,
        key: String,
    ) = long(obj, key).toInt()

    /** The GROSS profile of a salary section; the sheet stores the IRS fraction paid, the profile the exempt share. */
    private fun salaryProfile(
        key: String,
        flexPpm: Int? = null,
    ): SalaryProfile {
        val salary = section(key)
        return SalaryProfile(
            inputMode = SalaryInputMode.GROSS,
            baseGrossCents = long(salary, "baseCents"),
            subsidySpreadPpm = int(salary, "spreadPpm"),
            irsBasePpm = int(salary, "irsBasePpm"),
            irsSubsidiesPpm = int(salary, "irsSubsidiesPpm"),
            ssPpm = int(salary, "ssPpm"),
            irsJovemExemptPpm = PPM_SCALE - int(salary, "irsPaidFractionPpm"),
            flexPpm = flexPpm,
        )
    }

    private fun breakdown(profile: SalaryProfile) = requireNotNull(salaryBreakdownOf(profile))

    @Test
    fun salary_netMonthly_matchesSpreadsheetExactly_forEveryYear() {
        listOf("salary2024", "salary2025", "salary2026").forEach { key ->
            assertEquals(
                key,
                long(section(key), "expectedNetMonthlyCents"),
                breakdown(salaryProfile(key)).netMonthlyCents,
            )
        }
    }

    @Test
    fun salary_flex_annualAndQuarters_matchSpreadsheet() {
        val expected = section("expected")
        val result = breakdown(salaryProfile("salary2026", flexPpm = FLEX_PPM))

        assertEquals(long(expected, "flexAnnualCents"), result.flexAnnualCents)
        assertEquals(List(FLEX_PAYMENTS) { long(expected, "flexQuarterCents") }, result.flexQuarterlyCents)
    }

    @Test
    fun salary_holidayLumpNet_matchesSpreadsheet() {
        val result = breakdown(salaryProfile("salary2026"))

        assertEquals(long(section("expected"), "lumpSumNetCents"), result.holidayLumpNetCents)
        assertEquals(result.holidayLumpNetCents, result.christmasLumpNetCents)
    }

    // TODO(annual-plan): once the annual plan lands, assert expected.annualRecurringCents, annualInvestmentsCents,
    //  annualFoodCents, annualFoodLeftoverCents (honouring food.snacksSkipMonth) and the annual
    //  IRS figures (annualIrsCorrectCents, annualIrsSheetCents) against the sheet.

    private companion object {
        const val RESOURCE = "private/spreadsheet.json"
        const val FLEX_PPM = 70_000
    }
}

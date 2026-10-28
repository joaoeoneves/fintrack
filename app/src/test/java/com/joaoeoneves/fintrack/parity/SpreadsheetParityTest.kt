package com.joaoeoneves.fintrack.parity

import com.joaoeoneves.fintrack.domain.model.AnnualPlan
import com.joaoeoneves.fintrack.domain.model.AnnualPlanInputs
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.PPM_SCALE
import com.joaoeoneves.fintrack.domain.model.PlanMonth
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.amountIn
import com.joaoeoneves.fintrack.domain.model.annualPlanOf
import com.joaoeoneves.fintrack.domain.model.envelopesOf
import com.joaoeoneves.fintrack.domain.model.salaryBreakdownOf
import com.joaoeoneves.fintrack.testutil.flexExtra
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
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

    private fun mealLine() =
        PlannedIncome(
            id = "meal",
            label = "Meal allowance",
            amountCents = long("mealRateCents"),
            order = 1,
            basis = IncomeBasis.PER_WORKING_DAY,
        )

    @Test
    fun meal_perWorkingDayLine_matchesSpreadsheetMonthlyTotal() {
        val days = long("mealDaysPerMonth").toInt()

        assertEquals(long("expectedMealMonthlyCents"), mealLine().amountIn(month, days))
    }

    @Test
    fun envelopes_foodFundingAndLivre_matchSpreadsheet() {
        val food = section("food")
        val meal = mealLine()
        val group = Envelope("g-food", "Food", 0, fundedByLineId = meal.id)
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

        val summary =
            envelopesOf(
                buckets = buckets,
                categories = listOf(dinners, snacks, recurring, investments),
                envelopes = listOf(group),
                plannedIncome = listOf(salary, meal),
                monthIncome = emptyList(),
                planMonth = PlanMonth(month, long("mealDaysPerMonth").toInt()),
            )

        val expected = section("expected")
        val foodEnvelope = summary.envelopes.single()
        assertEquals(group.id, foodEnvelope.envelope.id)
        // Nothing is spent here, so what the funding leaves after the plan is funding minus the envelope's amount.
        assertEquals(long(expected, "foodPotFreeCents"), foodEnvelope.fundingCents!! - foodEnvelope.amountCents!!)
        assertEquals(long(expected, "baseFreeCents"), summary.freeCents)
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
            extras = listOfNotNull(flexPpm?.let { flexExtra(ppm = it.toLong()) }),
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

        val flex = result.extras.single()
        assertEquals(long(expected, "flexAnnualCents"), flex.totalCents)
        assertEquals(List(FLEX_QUARTERS) { long(expected, "flexQuarterCents") }, flex.payments.map { it.second })
    }

    // The sheet taxes the lump at the subsidy rate; the payslips withhold it at the base rate,
    // so this checks the payslip value.
    @Test
    fun salary_holidayLumpNet_matchesPayslip() {
        val result = breakdown(salaryProfile("salary2026"))

        assertEquals(long(section("expected"), "lumpSumNetPayslipCents"), result.holidayLumpNetCents)
        assertEquals(result.holidayLumpNetCents, result.christmasLumpNetCents)
    }

    private fun recurringItem(
        id: String,
        category: Category,
        cents: Long,
        yearly: Boolean = false,
    ) = RecurringItem(
        id = id,
        type = RecurringType.EXPENSE,
        name = id,
        amountCents = cents,
        categoryId = category.id,
        note = null,
        frequency = if (yearly) RecurringFrequency.YEARLY else RecurringFrequency.MONTHLY,
        dayOfMonth = 1,
        monthOfYear = if (yearly) YEARLY_ITEM_MONTH else null,
        startDate = LocalDate.of(YEAR, 1, 1),
        endDate = null,
        handledThrough = null,
        active = true,
    )

    /** The year the way the sheet builds it; see the assertions for which figures it must reproduce. */
    private fun annualPlan(): AnnualPlan {
        val food = section("food")
        val skipMonth = int(food, "snacksSkipMonth")
        val groups =
            listOf(
                Envelope("g-recurring", "Recurring", 0),
                Envelope("g-invest", "Investments", 1),
                Envelope("g-food", "Food", 2, fundedByLineId = "meal"),
            )
        val recurring = spendingCategory("recurring", "g-recurring")
        val investments = spendingCategory("investments", "g-invest")
        val dinners = spendingCategory("dinners", "g-food")
        val snacks = spendingCategory("snacks", "g-food")
        val items =
            longs("recurringMonthlyCents").mapIndexed { i, c -> recurringItem("m$i", recurring, c) } +
                longs("recurringYearlyCents").mapIndexed { i, c -> recurringItem("y$i", recurring, c, yearly = true) } +
                section("investmentsMonthlyCents").entries.map { (k, v) ->
                    recurringItem(k, investments, v.jsonPrimitive.content.toLong())
                }
        val meal =
            mealLine().copy(skipMonths = setOf(skipMonth))
        val overrides = (1..MONTHS).map { MonthSettings(YearMonth.of(YEAR, it), long("mealDaysPerMonth").toInt()) }
        return annualPlanOf(
            YEAR,
            AnnualPlanInputs(
                lines = listOf(meal),
                budgets =
                    listOf(
                        Budget(dinners.id, long(food, "dinnersMonthlyCents")),
                        Budget(snacks.id, long(food, "snacksMonthlyCents"), setOf(skipMonth)),
                    ),
                recurring = items,
                categories = listOf(recurring, investments, dinners, snacks),
                envelopes = groups,
                monthSettings = overrides,
            ),
        )
    }

    private fun AnnualPlan.planned(groupId: String) = envelopes.first { it.envelope?.id == groupId }.plannedCents

    @Test
    fun annual_recurringGroup_matchesSpreadsheet() {
        assertEquals(long(section("expected"), "annualRecurringCents"), annualPlan().planned("g-recurring"))
    }

    @Test
    fun annual_investmentsGroup_matchesSpreadsheet() {
        assertEquals(long(section("expected"), "annualInvestmentsCents"), annualPlan().planned("g-invest"))
    }

    @Test
    fun annual_foodGroup_matchesSpreadsheet() {
        assertEquals(long(section("expected"), "annualFoodCents"), annualPlan().planned("g-food"))
    }

    @Test
    fun annual_foodPotLeftover_matchesSpreadsheet() {
        val funding = annualPlan().funding.single()

        assertEquals("g-food", funding.envelope.id)
        assertEquals(long(section("expected"), "annualFoodLeftoverCents"), funding.leftoverCents)
    }

    // TODO(annual-plan): assert the annual IRS figures (expected.annualIrsCorrectCents, annualIrsSheetCents).

    private companion object {
        const val RESOURCE = "private/spreadsheet.json"
        const val FLEX_PPM = 70_000
        const val FLEX_QUARTERS = 4
        const val YEAR = 2026
        const val MONTHS = 12
        const val YEARLY_ITEM_MONTH = 3
    }
}

package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * The one month an income belongs to, used by every list, total, history and the meal card: a payday income
 * (`pay_<lineId>_<yyyyMM>`) belongs to the `yyyyMM` in its id, wherever its date fell (a December salary ticked
 * on 2 January is December's); any other income belongs to the month of its date in [zone].
 */
fun Income.ownerMonth(zone: ZoneId): YearMonth = parsePaydayIncomeId(id)?.second ?: YearMonth.from(date.atZone(zone))

/**
 * The incomes of [incomes] that belong to [range]: by their date, except a payday income dated outside its own
 * month, which counts where its id says (that month's mid-month noon must fall inside [range]). Pass a fetch padded
 * by a month on each side so such incomes are there to be re-attributed.
 */
fun filterOwnedIn(
    incomes: Iterable<Income>,
    range: InstantRange,
    zone: ZoneId,
): List<Income> =
    incomes.filter { income ->
        val owner = income.ownerMonth(zone)
        if (owner == YearMonth.from(income.date.atZone(zone))) {
            income.date in range
        } else {
            owner
                .atDay(MID_MONTH_DAY)
                .atTime(LocalTime.NOON)
                .atZone(zone)
                .toInstant() in range
        }
    }

/** The incomes of [incomes] owned by [month] (see [ownerMonth]). */
fun filterOwnedInMonth(
    incomes: Iterable<Income>,
    month: YearMonth,
    zone: ZoneId,
): List<Income> = incomes.filter { it.ownerMonth(zone) == month }

private const val MID_MONTH_DAY = 15

/** Whether this row of [month] can be marked received: its pay date ([payDateIn]) is today or past. */
fun IncomeRow.canReceiveIn(
    month: YearMonth,
    income: IncomeMonth,
    today: LocalDate,
): Boolean = payDateIn(month, income) <= today

package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate
import java.time.Month

private const val GOOD_FRIDAY_OFFSET_DAYS = -2L
private const val CORPUS_CHRISTI_OFFSET_DAYS = 60L
private const val CARNIVAL_OFFSET_DAYS = -47L

// Calendar dates (month, day), self-describing as written.
@Suppress("MagicNumber")
private val FIXED_HOLIDAYS =
    listOf(
        Month.JANUARY to 1,
        Month.APRIL to 25,
        Month.MAY to 1,
        Month.JUNE to 10,
        Month.AUGUST to 15,
        Month.OCTOBER to 5,
        Month.NOVEMBER to 1,
        Month.DECEMBER to 1,
        Month.DECEMBER to 8,
        Month.DECEMBER to 25,
    )

/**
 * Easter Sunday of [year] (Gregorian calendar), by the Meeus/Jones/Butcher algorithm. The literals
 * are the algorithm's own constants, so naming them would not make it any clearer.
 */
@Suppress("MagicNumber")
fun easterSunday(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - b / 4 - g + 15) % 30
    val l = (32 + 2 * (b % 4) + 2 * (c / 4) - h - c % 4) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val n = h + l - 7 * m + 114
    return LocalDate.of(year, n / 31, n % 31 + 1)
}

/**
 * The national public holidays of Portugal in [year]: the ten fixed ones plus Good Friday, Easter
 * Sunday and Corpus Christi. Carnival and municipal holidays are not included.
 */
fun portugueseNationalHolidays(year: Int): Set<LocalDate> {
    val easter = easterSunday(year)
    val fixed = FIXED_HOLIDAYS.map { (month, day) -> LocalDate.of(year, month, day) }
    return (
        fixed +
            easter.plusDays(GOOD_FRIDAY_OFFSET_DAYS) +
            easter +
            easter.plusDays(CORPUS_CHRISTI_OFFSET_DAYS)
    ).toSet()
}

/** Carnival Tuesday of [year] (Easter minus 47 days): an optional holiday, deliberately not a national one. */
fun carnivalTuesday(year: Int): LocalDate = easterSunday(year).plusDays(CARNIVAL_OFFSET_DAYS)

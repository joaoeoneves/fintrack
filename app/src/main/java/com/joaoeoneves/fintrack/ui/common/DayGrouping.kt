package com.joaoeoneves.fintrack.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.SectionLabel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A run of consecutive transactions that fall on the same local calendar [date]. */
data class DaySection<T>(
    val date: LocalDate,
    val items: List<T>,
)

/**
 * Groups [items] into sections of consecutive same-day runs in [zone], keeping the input order
 * (it does not sort, so a descending list gives newest-day-first sections). A day that reappears
 * after a different day starts a new section.
 */
fun <T : Transaction> groupByDay(
    items: List<T>,
    zone: ZoneId,
): List<DaySection<T>> {
    val sections = mutableListOf<DaySection<T>>()
    var currentDate: LocalDate? = null
    var currentItems = mutableListOf<T>()
    for (item in items) {
        val date = item.date.atZone(zone).toLocalDate()
        if (date != currentDate) {
            currentDate?.let { sections += DaySection(it, currentItems.toList()) }
            currentDate = date
            currentItems = mutableListOf()
        }
        currentItems += item
    }
    currentDate?.let { sections += DaySection(it, currentItems.toList()) }
    return sections
}

/** How a day section is titled: relative for today/yesterday, otherwise by calendar date. */
sealed interface DayHeader {
    data object Today : DayHeader

    data object Yesterday : DayHeader

    /** [showYear] is set when the date is not in the same year as today. */
    data class Date(
        val date: LocalDate,
        val showYear: Boolean,
    ) : DayHeader
}

fun dayHeaderOf(
    date: LocalDate,
    today: LocalDate,
): DayHeader =
    when (date) {
        today -> DayHeader.Today
        today.minusDays(1) -> DayHeader.Yesterday
        else -> DayHeader.Date(date, showYear = date.year != today.year)
    }

/**
 * "Monday, 20 October" (or "20 October 2025" when [showYear]); Portuguese uses "segunda-feira, 20
 * de outubro" / "20 de outubro de 2025". Deliberately never a bare "MMMM yyyy": that shape is
 * reserved for the period label.
 */
fun formatDayHeaderDate(
    date: LocalDate,
    showYear: Boolean,
    locale: Locale,
): String {
    val portuguese = locale.language == "pt"
    val pattern =
        when {
            portuguese && showYear -> "d 'de' MMMM 'de' yyyy"
            portuguese -> "EEEE, d 'de' MMMM"
            showYear -> "d MMMM yyyy"
            else -> "EEEE, d MMMM"
        }
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

/** Small section heading for a [DaySection] in a transaction list. */
@Composable
fun DaySectionHeader(
    header: DayHeader,
    modifier: Modifier = Modifier,
) {
    val text =
        when (header) {
            DayHeader.Today -> stringResource(R.string.day_header_today)
            DayHeader.Yesterday -> stringResource(R.string.day_header_yesterday)
            is DayHeader.Date -> formatDayHeaderDate(header.date, header.showYear, currentLocale())
        }
    Text(
        text = text,
        style = SectionLabel,
        color = FinTrackTheme.colors.textSecondary,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = Dimens.Space16, bottom = Dimens.Space4)
                .semantics { heading() },
    )
}

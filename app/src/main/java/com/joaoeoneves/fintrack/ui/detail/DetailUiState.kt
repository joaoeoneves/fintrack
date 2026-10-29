package com.joaoeoneves.fintrack.ui.detail

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.SalaryComponent
import com.joaoeoneves.fintrack.domain.model.Transaction
import java.time.YearMonth

/** Where a detail page's "Edit" action leads. */
sealed interface DetailEditTarget {
    data class CategoryEditor(
        val id: String,
    ) : DetailEditTarget

    data object SalaryProfile : DetailEditTarget

    /** The income screen of [month] (an encoded `Period` token), where a manual line is edited. */
    data class IncomeLines(
        val month: String,
    ) : DetailEditTarget
}

/** One bar of the 12-month chart. */
data class DetailMonth(
    val month: YearMonth,
    val valueCents: Long,
)

/** The change against the average of the earlier months; [percent] is signed. */
data class DetailDeltaFigure(
    val percent: Int,
)

/** The page's subject: a category or an income source ([component] and/or free [label]). */
sealed interface DetailSubject {
    data class OfCategory(
        val category: Category,
        val envelopeName: String?,
        val limitCents: Long?,
    ) : DetailSubject

    data class OfSource(
        val component: SalaryComponent?,
        val label: String?,
        val expectedCents: Long?,
    ) : DetailSubject
}

sealed interface DetailUiState {
    data object Loading : DetailUiState

    /** The id or key matches nothing (stale link). */
    data object NotFound : DetailUiState

    data class Content(
        val subject: DetailSubject,
        val months: List<DetailMonth>,
        val selectedIndex: Int,
        val delta: DetailDeltaFigure?,
        val movements: List<Transaction>,
        val editTarget: DetailEditTarget?,
    ) : DetailUiState {
        val selected: DetailMonth get() = months[selectedIndex]
        val isIncome: Boolean get() = subject is DetailSubject.OfSource
        val hasData: Boolean get() = months.any { it.valueCents != 0L } || movements.isNotEmpty()
    }
}

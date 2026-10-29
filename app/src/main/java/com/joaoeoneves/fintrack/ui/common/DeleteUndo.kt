package com.joaoeoneves.fintrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.domain.usecase.DeleteTransaction
import com.joaoeoneves.fintrack.domain.usecase.DeletedTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Shows the app-level "Deleted ... / Undo" snackbar for a transaction deleted from a form that then closes;
 * the default does nothing (previews, tests).
 */
val LocalDeleteUndo = staticCompositionLocalOf<(DeletedTransaction) -> Unit> { {} }

/** Hands each deletion emitted by [deleted] to the app-level undo snackbar, then calls [onDeleted]. */
@Composable
fun DeletedEffect(
    deleted: Flow<DeletedTransaction>,
    onDeleted: () -> Unit,
) {
    val showDeleted = LocalDeleteUndo.current
    LaunchedEffect(Unit) {
        deleted.collectLatest { transaction ->
            showDeleted(transaction)
            onDeleted()
        }
    }
}

/**
 * The snackbar text for [deleted]: the plain template with the transaction's title, or, when the deletion
 * skipped a recurring occurrence, [occurrenceTemplate] with the item's name and the occurrence's day and month.
 */
fun deletedMessage(
    deleted: DeletedTransaction,
    plainTemplate: String,
    occurrenceTemplate: String,
    locale: Locale,
): String {
    val ignored = deleted.ignored
    return if (ignored == null) {
        String.format(locale, plainTemplate, deleted.transaction.title())
    } else {
        val day = formatDayMonth(ignored.date, locale)
        String.format(locale, occurrenceTemplate, ignored.itemName, day)
    }
}

/** [deletedMessage] with the app's strings and locale. */
@Composable
fun rememberDeletedMessage(): (DeletedTransaction) -> String {
    val plainTemplate = stringResource(R.string.activity_deleted_snackbar)
    val occurrenceTemplate = stringResource(R.string.activity_deleted_occurrence_snackbar)
    val locale = currentLocale()
    return remember(plainTemplate, occurrenceTemplate, locale) {
        { deleted -> deletedMessage(deleted, plainTemplate, occurrenceTemplate, locale) }
    }
}

private fun Transaction.title(): String =
    when (this) {
        is Expense -> name
        is Income -> source
    }

/** Restores a transaction whose deletion the user undid from the app-level snackbar. */
@HiltViewModel
class TransactionUndoViewModel
    @Inject
    constructor(
        private val deleteTransaction: DeleteTransaction,
    ) : ViewModel() {
        /** Re-adds the [deleted] transaction under its original id and un-skips the occurrence its deletion skipped. */
        fun restore(deleted: DeletedTransaction) {
            viewModelScope.launch { deleteTransaction.undo(deleted) }
        }
    }

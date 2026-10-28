package com.joaoeoneves.fintrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shows the app-level "Deleted … / Undo" snackbar for a transaction deleted from a form that then closes;
 * the default does nothing (previews, tests).
 */
val LocalDeleteUndo = staticCompositionLocalOf<(Transaction) -> Unit> { {} }

/** Hands each transaction emitted by [deleted] to the app-level undo snackbar, then calls [onDeleted]. */
@Composable
fun DeletedEffect(
    deleted: Flow<Transaction>,
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

/** Restores a transaction whose deletion the user undid from the app-level snackbar. */
@HiltViewModel
class TransactionUndoViewModel
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val incomeRepository: IncomeRepository,
    ) : ViewModel() {
        /** Re-adds a deleted [transaction] under its original id. */
        fun restore(transaction: Transaction) {
            viewModelScope.launch {
                when (transaction) {
                    is Expense -> expenseRepository.addExpense(transaction)
                    is Income -> incomeRepository.addIncome(transaction)
                }
            }
        }
    }

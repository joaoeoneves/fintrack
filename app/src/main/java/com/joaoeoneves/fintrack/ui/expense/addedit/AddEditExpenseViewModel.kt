package com.joaoeoneves.fintrack.ui.expense.addedit

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MAX_NOTE_LENGTH
import com.joaoeoneves.fintrack.domain.model.active
import com.joaoeoneves.fintrack.domain.model.categorySuggestionIndex
import com.joaoeoneves.fintrack.domain.model.defaultCategory
import com.joaoeoneves.fintrack.domain.model.limitInput
import com.joaoeoneves.fintrack.domain.model.normalizedMerchantName
import com.joaoeoneves.fintrack.domain.model.usesMealCard
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import com.joaoeoneves.fintrack.domain.usecase.DeleteTransaction
import com.joaoeoneves.fintrack.domain.usecase.DeletedTransaction
import com.joaoeoneves.fintrack.ui.common.FormSnapshotStore
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.defaultNewTransactionDate
import com.joaoeoneves.fintrack.ui.navigation.AddEditExpense
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import javax.inject.Inject

private const val TAG = "AddEditExpenseViewModel"
private const val FORM_KEY = "expense_form"

// How long (in ms) the category listener is kept alive after the last collector disappears.
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

// How far back (in months) the category suggestions look.
private const val SUGGESTION_HISTORY_MONTHS = 12L
private const val DAY_SECONDS = 86_400L

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions", "LongParameterList") // One handler per form field; the flows are test seams.
@HiltViewModel
class AddEditExpenseViewModel(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val deleteTransaction: DeleteTransaction,
    savedStateHandle: SavedStateHandle,
    /** Whether the meal-card switch applies (see `usesMealCard`); the switch is hidden while it does not. */
    private val mealCardAvailable: Flow<Boolean> = flowOf(false),
    private val clock: Clock = Clock.systemDefaultZone(),
    /** Whether a month's meal-card plafond is used up (new expenses in it then default to the bank). */
    private val mealCardPlafondUsed: (YearMonth) -> Flow<Boolean> = { flowOf(false) },
) : ViewModel() {
    @Inject
    constructor(
        expenseRepository: ExpenseRepository,
        categoryRepository: CategoryRepository,
        deleteTransaction: DeleteTransaction,
        savedStateHandle: SavedStateHandle,
        salaryProfiles: SalaryProfileRepository,
        incomeRepository: IncomeRepository,
        plannedIncomeRepository: PlannedIncomeRepository,
        clock: Clock,
    ) : this(
        expenseRepository,
        categoryRepository,
        deleteTransaction,
        savedStateHandle,
        salaryProfiles.observeSalaryProfile().map { it.usesMealCard() }.catch { emit(false) },
        clock,
        mealCardPlafondUsedIn(expenseRepository, incomeRepository, plannedIncomeRepository, clock.zone),
    )

    private val route = savedStateHandle.toRoute<AddEditExpense>()

    private val snapshots = FormSnapshotStore(savedStateHandle, FORM_KEY, ExpenseFormSnapshot.serializer())

    val isEditRoute: Boolean = route.expenseId != null

    private val _uiState = MutableStateFlow<AddEditUiState>(AddEditUiState.Loading)
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    /** The full category catalog (archived included); the picker offers its active entries. */
    val categoryOptions: StateFlow<List<Category>> =
        categoryRepository
            .observeCategories()
            .catch { emit(BuiltInCategories.all) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), BuiltInCategories.all)

    private val savedEvents = Channel<Unit>(Channel.BUFFERED)
    val savedEvent: Flow<Unit> = savedEvents.receiveAsFlow()

    private val deletedEvents = Channel<DeletedTransaction>(Channel.BUFFERED)

    /** Emits the deleted expense (as loaded) so the screen can offer to undo the deletion. */
    val deletedEvent: Flow<DeletedTransaction> = deletedEvents.receiveAsFlow()

    // The expense as loaded in edit mode, kept so a deletion can be undone.
    private var loaded: Expense? = null

    private var suggestionIndex: Map<String, CategoryId> = emptyMap()

    // Whether the meal-card switch applies right now; forms built after the profile loaded start with it.
    private var mealAvailable = false
    private var plafondUsed = false

    init {
        loadExpense()
        viewModelScope.launch {
            mealCardAvailable.collect { available ->
                mealAvailable = available
                updateForm { it.copy(mealCardAvailable = available).withMealCardPrefill() }
            }
        }
        viewModelScope.launch {
            // The plafond of the month the form's date is in; a new date in another month re-evaluates it.
            _uiState
                .map { state -> (state as? AddEditUiState.Ready)?.form?.date?.let(::monthOf) }
                .filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest(mealCardPlafondUsed)
                .collect { used ->
                    plafondUsed = used
                    updateForm { it.copy(mealCardPlafondUsed = used).withMealCardPrefill() }
                }
        }
    }

    private fun monthOf(date: Instant): YearMonth = YearMonth.from(date.atZone(clock.zone))

    private fun loadExpense() {
        viewModelScope.launch {
            _uiState.value = AddEditUiState.Loading
            val expenseId = route.expenseId
            if (expenseId != null) {
                expenseRepository
                    .getExpense(expenseId)
                    .onSuccess { existing ->
                        loaded = existing
                        _uiState.value =
                            if (existing != null) {
                                readyWithSaved(
                                    ExpenseFormState(
                                        name = existing.name,
                                        amountCents = existing.amountCents,
                                        category = existing.category,
                                        date = existing.date,
                                        note = existing.note ?: "",
                                        isRefund = existing.isRefund,
                                        paidWithMealCard = existing.paidWithMealCard,
                                        mealCardAvailable = mealAvailable,
                                        isEditMode = true,
                                        expenseId = existing.id,
                                    ),
                                    BuiltInCategories.all,
                                )
                            } else {
                                AddEditUiState.Error(UiText.StringResource(R.string.detail_not_found))
                            }
                    }.onFailure { e ->
                        Log.w(TAG, "Failed to load expense", e)
                        _uiState.value =
                            AddEditUiState.Error(UiText.StringResource(R.string.error_generic_fallback))
                    }
            } else {
                val category =
                    defaultCategory(
                        categoryRepository.observeCategories().catch { emit(BuiltInCategories.all) }.first(),
                    )
                _uiState.value =
                    readyWithSaved(
                        ExpenseFormState(
                            category = category,
                            date = defaultNewTransactionDate(route.period, clock.instant(), clock.zone),
                            mealCardAvailable = mealAvailable,
                            mealCardPlafondUsed = plafondUsed,
                        ).withMealCardPrefill(),
                        categoryOptions.value,
                    )
                suggestionIndex = loadSuggestionIndex(expenseRepository, clock)
            }
        }
    }

    /** The loaded [form] with what was typed before the process died, if anything. */
    private fun readyWithSaved(
        form: ExpenseFormState,
        catalog: List<Category>,
    ): AddEditUiState.Ready = AddEditUiState.Ready(form.restoredFrom(snapshots.read(), catalog))

    fun onRetry() = loadExpense()

    fun onNameChanged(name: String) =
        updateForm {
            val limited = limitInput(it.name, name, MAX_NAME_LENGTH)
            val updated = it.copy(name = limited, nameError = null, saveError = null)
            if (it.isEditMode || it.categoryTouched) {
                updated
            } else {
                updated.withSuggestion(suggestionIndex, categoryOptions.value).withMealCardPrefill()
            }
        }

    fun onAmountChanged(amountCents: Long) {
        updateForm { it.copy(amountCents = amountCents, amountError = null, saveError = null) }
    }

    fun onCategorySelected(category: Category) {
        updateForm {
            it
                .copy(
                    category = category,
                    categoryTouched = true,
                    suggestedCategory = false,
                    categoryError = null,
                    saveError = null,
                ).withMealCardPrefill()
        }
    }

    fun onDateSelected(date: Instant) = updateForm { it.copy(date = date, saveError = null) }

    fun onNoteChanged(note: String) {
        updateForm { it.copy(note = limitInput(it.note, note, MAX_NOTE_LENGTH), saveError = null) }
    }

    fun onRefundChanged(isRefund: Boolean) {
        updateForm { it.copy(isRefund = isRefund, saveError = null).withMealCardPrefill() }
    }

    /** The "Paid with meal card" switch; once the user sets it, the category no longer decides. */
    fun onMealCardChanged(paid: Boolean) = updateForm { it.copy(paidWithMealCard = paid, mealCardTouched = true) }

    fun onSave() {
        val current = _uiState.value
        if (current !is AddEditUiState.Ready || current.form.isSaving) return
        val form = current.form

        val nameError = if (form.name.isBlank()) UiText.StringResource(R.string.error_name_required) else null
        val amountError = if (form.amountCents <= 0L) UiText.StringResource(R.string.error_invalid_amount) else null
        // An unknown category (e.g. deleted since the expense was saved) must be replaced first.
        val categoryError =
            if (form.category.isUnknown) UiText.StringResource(R.string.error_category_required) else null

        if (nameError != null || amountError != null || categoryError != null) {
            _uiState.value =
                AddEditUiState.Ready(
                    form.copy(nameError = nameError, amountError = amountError, categoryError = categoryError),
                )
            return
        }

        val amountCents = form.amountCents
        _uiState.value = AddEditUiState.Ready(form.copy(isSaving = true))
        viewModelScope.launch {
            val expense =
                Expense(
                    id = form.expenseId.orEmpty(),
                    name = form.name.trim(),
                    amountCents = amountCents,
                    category = form.category,
                    date = form.date,
                    note = form.note.trim().ifBlank { null },
                    isRefund = form.isRefund,
                    paidWithMealCard = form.paidWithMealCard,
                )
            val result =
                if (form.isEditMode) {
                    expenseRepository.updateExpense(expense)
                } else {
                    expenseRepository.addExpense(expense)
                }
            result
                .onSuccess {
                    savedEvents.send(Unit)
                }.onFailure { e ->
                    val latest = _uiState.value
                    if (latest is AddEditUiState.Ready) {
                        Log.w(TAG, "Failed to save expense", e)
                        val saveError = UiText.StringResource(R.string.error_failed_save_expense)
                        _uiState.value =
                            AddEditUiState.Ready(latest.form.copy(isSaving = false, saveError = saveError))
                    }
                }
        }
    }

    /** Deletes the expense being edited; the screen then closes and offers an undo. */
    fun onDelete() {
        val current = _uiState.value
        val expense = loaded
        if (current !is AddEditUiState.Ready || !current.form.isEditMode || expense == null) return
        if (current.form.isSaving || current.form.isDeleting) return
        _uiState.value = AddEditUiState.Ready(current.form.copy(isDeleting = true, saveError = null))
        viewModelScope.launch {
            deleteTransaction(expense)
                .onSuccess {
                    deletedEvents.send(it)
                }.onFailure { e ->
                    Log.w(TAG, "Failed to delete expense", e)
                    updateForm {
                        it.copy(
                            isDeleting = false,
                            saveError = UiText.StringResource(R.string.error_failed_delete_expense),
                        )
                    }
                }
        }
    }

    private inline fun updateForm(transform: (ExpenseFormState) -> ExpenseFormState) {
        val current = _uiState.value
        if (current is AddEditUiState.Ready) {
            val updated = transform(current.form)
            snapshots.write(updated.toSnapshot())
            _uiState.value = AddEditUiState.Ready(updated)
        }
    }
}

/** This form with the category last used for its name (see [categorySuggestionIndex]), if any. */
private fun ExpenseFormState.withSuggestion(
    index: Map<String, CategoryId>,
    catalog: List<Category>,
): ExpenseFormState {
    val suggested = index[normalizedMerchantName(name)]?.let { id -> catalog.active().find { it.id == id } }
    return when {
        suggested != null -> copy(category = suggested, suggestedCategory = true)
        suggestedCategory -> copy(category = defaultCategory(catalog), suggestedCategory = false)
        else -> this
    }
}

/** The category suggestions from the last year of expenses; any failure just means no suggestions. */
private suspend fun loadSuggestionIndex(
    repository: ExpenseRepository,
    clock: Clock,
): Map<String, CategoryId> =
    runCatching {
        val now = clock.instant()
        val start = now.atZone(clock.zone).minusMonths(SUGGESTION_HISTORY_MONTHS).toInstant()
        val expenses = repository.observeExpenses(start, now.plusSeconds(DAY_SECONDS)).first()
        categorySuggestionIndex(expenses)
    }.onFailure { Log.w(TAG, "Failed to load category suggestions", it) }
        .getOrDefault(emptyMap())

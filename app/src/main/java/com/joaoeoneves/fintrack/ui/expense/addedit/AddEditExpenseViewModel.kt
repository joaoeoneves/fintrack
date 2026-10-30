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
import com.joaoeoneves.fintrack.domain.model.MAX_SPLIT_PARTS
import com.joaoeoneves.fintrack.domain.model.NameSuggestion
import com.joaoeoneves.fintrack.domain.model.SplitPart
import com.joaoeoneves.fintrack.domain.model.active
import com.joaoeoneves.fintrack.domain.model.categorySuggestionIndex
import com.joaoeoneves.fintrack.domain.model.defaultCategory
import com.joaoeoneves.fintrack.domain.model.duplicated
import com.joaoeoneves.fintrack.domain.model.limitInput
import com.joaoeoneves.fintrack.domain.model.nameSuggestions
import com.joaoeoneves.fintrack.domain.model.normalizedMerchantName
import com.joaoeoneves.fintrack.domain.model.refundCandidates
import com.joaoeoneves.fintrack.domain.model.refundStatusOf
import com.joaoeoneves.fintrack.domain.model.splitExpenses
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
import java.util.UUID
import javax.inject.Inject

private const val TAG = "AddEditExpenseViewModel"
private const val FORM_KEY = "expense_form"

// How long (in ms) the category listener is kept alive after the last collector disappears.
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

// How far back (in months) the category suggestions look.
private const val SUGGESTION_HISTORY_MONTHS = 12L
private const val DAY_SECONDS = 86_400L
private const val MIN_SPLIT_PARTS = 2
private val EMPTY_PART = SplitPartForm(null, 0L)

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
    /** The id shared by the parts of a new split. */
    private val idFactory: () -> String = { UUID.randomUUID().toString().replace("-", "") },
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

    // The last year of expenses, loaded once when a new form opens; the category index derives from it.
    private var history: List<Expense> = emptyList()
    private var suggestionIndex: Map<String, CategoryId> = emptyMap()

    // Whether the meal-card switch applies right now; forms built after the profile loaded start with it.
    private var mealAvailable = false
    private var plafondUsed = false

    // Every refund linked to some original; the edited expense's own refund status derives from it.
    private var linkedRefunds: List<Expense> = emptyList()

    init {
        loadExpense()
        if (route.expenseId != null) {
            viewModelScope.launch {
                expenseRepository
                    .observeLinkedRefunds()
                    .catch { Log.w(TAG, "Failed to observe linked refunds", it) }
                    .collect { refunds ->
                        linkedRefunds = refunds
                        applyRefundStatus()
                    }
            }
        }
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
                                        refundOf = existing.refundOf,
                                        splitId = existing.splitId,
                                        refundOfLabel = existing.refundOf?.let { originalName(it) },
                                    ),
                                    BuiltInCategories.all,
                                )
                            } else {
                                AddEditUiState.Error(UiText.StringResource(R.string.detail_not_found))
                            }
                        if (existing != null) {
                            applyRefundStatus()
                            publishRefundCandidates(loadHistory(expenseRepository, clock))
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "Failed to load expense", e)
                        _uiState.value =
                            AddEditUiState.Error(UiText.StringResource(R.string.error_generic_fallback))
                    }
            } else {
                loadNewForm()
            }
        }
    }

    /** A new form: today's date, the default category and the route's prefill (a saved snapshot wins over it). */
    private suspend fun loadNewForm() {
        val catalog = categoryRepository.observeCategories().catch { emit(BuiltInCategories.all) }.first()
        val base =
            ExpenseFormState(
                category = defaultCategory(catalog),
                date = defaultNewTransactionDate(route.period, clock.instant(), clock.zone),
                mealCardAvailable = mealAvailable,
                mealCardPlafondUsed = plafondUsed,
            )
        val prefilled =
            base.withPrefill(route.prefillName, route.prefillAmountCents, route.prefillCategoryId, catalog)
        val seeded = seededFromRoute(prefilled)
        _uiState.value = readyWithSaved(seeded.withMealCardPrefill(), categoryOptions.value)
        history = loadHistory(expenseRepository, clock)
        publishRefundCandidates(history)
        suggestionIndex = categorySuggestionIndex(history)
        // A chip whose category is gone or archived falls back to the one last used for its name.
        if (route.prefillName != null && !prefilled.suggestedCategory && seeded === prefilled) {
            updateForm {
                if (it.categoryTouched) it else it.withSuggestion(suggestionIndex, catalog).withMealCardPrefill()
            }
        }
    }

    /** The new [form] seeded from the route: a duplicate copies the other expense; a missing source leaves it as is. */
    private suspend fun seededFromRoute(form: ExpenseFormState): ExpenseFormState {
        val source = route.duplicateOf?.let { id -> expenseRepository.getExpense(id).getOrNull() } ?: return form
        val copy = source.duplicated(clock.instant())
        return form.copy(
            name = copy.name,
            amountCents = copy.amountCents,
            category = copy.category,
            date = copy.date,
            note = copy.note.orEmpty(),
            isRefund = copy.isRefund,
            paidWithMealCard = copy.paidWithMealCard,
            categoryTouched = true,
            mealCardTouched = true,
        )
    }

    /** The name of the original expense [id], or `null` when it no longer exists. */
    private suspend fun originalName(id: String): String? = expenseRepository.getExpense(id).getOrNull()?.name

    private fun applyRefundStatus() {
        val original = loaded ?: return
        val status = if (original.isRefund) null else refundStatusOf(original, linkedRefunds)
        updateForm { it.copy(refundStatus = status) }
    }

    private fun publishRefundCandidates(expenses: List<Expense>) {
        val candidates = refundCandidates(expenses, route.expenseId)
        updateForm { it.copy(refundCandidates = candidates) }
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
            val updated =
                it.copy(
                    name = limited,
                    nameError = null,
                    saveError = null,
                    nameSuggestions = if (it.isEditMode) emptyList() else nameSuggestions(history, limited),
                )
            if (it.isEditMode || it.categoryTouched) {
                updated
            } else {
                updated.withSuggestion(suggestionIndex, categoryOptions.value).withMealCardPrefill()
            }
        }

    /** Fills in the name of a suggested earlier expense, with its category; never touches the amount. */
    fun onNameSuggestionPicked(suggestion: NameSuggestion) =
        updateForm {
            val named =
                it.copy(name = suggestion.name, nameError = null, saveError = null, nameSuggestions = emptyList())
            if (it.categoryTouched) {
                named
            } else {
                named.withSuggestion(suggestionIndex, categoryOptions.value).withMealCardPrefill()
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
        updateForm {
            val base = it.copy(isRefund = isRefund, saveError = null)
            val updated = if (isRefund) base.withoutSplit() else base
            // A link only means something on a refund.
            (if (isRefund) updated else updated.copy(refundOf = null, refundOfLabel = null)).withMealCardPrefill()
        }
    }

    /** Links this refund to [original], or unlinks it when `null`. */
    fun onRefundOfSelected(original: Expense?) {
        updateForm { it.copy(refundOf = original?.id, refundOfLabel = original?.name, saveError = null) }
    }

    /** The "Paid with meal card" switch; once the user sets it, the category no longer decides. */
    fun onMealCardChanged(paid: Boolean) = updateForm { it.copy(paidWithMealCard = paid, mealCardTouched = true) }

    fun onSave() {
        val current = _uiState.value
        if (current !is AddEditUiState.Ready || current.form.isSaving) return
        val form = current.form
        val invalid = form.withValidationErrors()
        if (invalid != null) {
            _uiState.value = AddEditUiState.Ready(invalid)
            return
        }
        _uiState.value = AddEditUiState.Ready(form.copy(isSaving = true))
        viewModelScope.launch {
            val expense = form.toExpense()
            val result =
                when {
                    form.isEditMode -> expenseRepository.updateExpense(expense)
                    form.isSplitting -> saveSplit(expense, form)
                    else -> expenseRepository.addExpense(expense)
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

    /** Saves the split as sibling expenses sharing one split id, keeping their ids. */
    private suspend fun saveSplit(
        base: Expense,
        form: ExpenseFormState,
    ): Result<*> {
        val parts = form.splitParts.map { SplitPart(checkNotNull(it.category), it.amountCents) }
        return runCatching { splitExpenses(base, parts, idFactory()) }
            .mapCatching { expenseRepository.addExpensesKeepingIds(it).getOrThrow() }
    }

    fun onSplitEnabledChanged(enabled: Boolean) =
        updateForm {
            if (!enabled) {
                it.withoutSplit()
            } else {
                it.copy(
                    splitEnabled = true,
                    splitParts =
                        listOf(
                            SplitPartForm(it.category.takeUnless { c -> c.isUnknown }, it.amountCents),
                            SplitPartForm(null, 0L),
                        ),
                    splitError = null,
                )
            }
        }

    fun onSplitPartCategoryChanged(
        index: Int,
        category: Category,
    ) = updateSplitPart(index) { it.copy(category = category) }

    fun onSplitPartAmountChanged(
        index: Int,
        amountCents: Long,
    ) = updateSplitPart(index) { it.copy(amountCents = amountCents) }

    fun onSplitPartAdded() =
        updateForm {
            if (it.splitParts.size >= MAX_SPLIT_PARTS) it else it.copy(splitParts = it.splitParts + EMPTY_PART)
        }

    fun onSplitPartRemoved(index: Int) =
        updateForm {
            if (it.splitParts.size <= MIN_SPLIT_PARTS) {
                it
            } else {
                it.copy(splitParts = it.splitParts.filterIndexed { i, _ -> i != index })
            }
        }

    private fun updateSplitPart(
        index: Int,
        transform: (SplitPartForm) -> SplitPartForm,
    ) = updateForm {
        it.copy(
            splitParts = it.splitParts.mapIndexed { i, part -> if (i == index) transform(part) else part },
            splitError = null,
            saveError = null,
        )
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

/** The last year of expenses, for the category and name suggestions; any failure just means no suggestions. */
private suspend fun loadHistory(
    repository: ExpenseRepository,
    clock: Clock,
): List<Expense> =
    runCatching {
        val now = clock.instant()
        val start = now.atZone(clock.zone).minusMonths(SUGGESTION_HISTORY_MONTHS).toInstant()
        repository.observeExpenses(start, now.plusSeconds(DAY_SECONDS)).first()
    }.onFailure { Log.w(TAG, "Failed to load suggestion history", it) }
        .getOrDefault(emptyList())

/** This form with its validation errors set, or `null` when it can be saved. */
private fun ExpenseFormState.withValidationErrors(): ExpenseFormState? {
    val nameError = if (name.isBlank()) UiText.StringResource(R.string.error_name_required) else null
    val amountError = if (amountCents <= 0L) UiText.StringResource(R.string.error_invalid_amount) else null
    // An unknown category (e.g. deleted since the expense was saved) must be replaced first.
    val categoryError =
        if (category.isUnknown && !isSplitting) UiText.StringResource(R.string.error_category_required) else null
    val splitError = splitValidationError()
    val errors = listOfNotNull(nameError, amountError, categoryError, splitError)
    return if (errors.isEmpty()) {
        null
    } else {
        copy(nameError = nameError, amountError = amountError, categoryError = categoryError, splitError = splitError)
    }
}

private fun ExpenseFormState.toExpense(): Expense =
    Expense(
        id = expenseId.orEmpty(),
        name = name.trim(),
        amountCents = amountCents,
        category = category,
        date = date,
        note = note.trim().ifBlank { null },
        isRefund = isRefund,
        paidWithMealCard = paidWithMealCard,
        refundOf = refundOf.takeIf { isRefund },
        splitId = splitId,
        // Saving counts as looking at it: an edited automatic record leaves the review inbox.
        needsReview = false,
    )

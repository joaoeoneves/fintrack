package com.joaoeoneves.fintrack.ui.plan.envelopes

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.limitInput
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import com.joaoeoneves.fintrack.domain.repository.EnvelopeRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.navigation.EditEnvelope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "EnvelopeEditorViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** A category in the group; [movedFromName] is the other group it is moved out of when saved. */
data class MemberUi(
    val category: Category,
    val movedFromName: String? = null,
)

/** A category that can be added; [inEnvelopeName] is the other envelope it would be moved out of. */
data class AddableCategoryUi(
    val category: Category,
    val inEnvelopeName: String?,
)

sealed interface EnvelopeEditorUiState {
    data object Loading : EnvelopeEditorUiState

    data object NotFound : EnvelopeEditorUiState

    data class Content(
        val draft: EnvelopeDraft,
        val nameError: UiText?,
        val members: List<MemberUi>,
        val addable: List<AddableCategoryUi>,
        val isNew: Boolean,
        val saving: Boolean,
    ) : EnvelopeEditorUiState
}

private class EditorData(
    val envelopes: List<Envelope>,
    val categories: List<Category>,
)

private class EditorFlags(
    val notFound: Boolean,
    val submitted: Boolean,
    val saving: Boolean,
)

/** Creates, edits and deletes one envelope; the draft is seeded once so repository updates never overwrite edits. */
@Suppress("TooManyFunctions") // One handler per user action on the screen.
@HiltViewModel
class EnvelopeEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val envelopeRepository: EnvelopeRepository,
        private val categoryRepository: CategoryRepository,
    ) : ViewModel() {
        private val envelopeId = savedStateHandle.toRoute<EditEnvelope>().envelopeId
        private val draft = MutableStateFlow<EnvelopeDraft?>(null)
        private val notFound = MutableStateFlow(false)
        private val submitted = MutableStateFlow(false)
        private val saving = MutableStateFlow(false)
        private val errors = Channel<UiText>(Channel.BUFFERED)
        private val back = Channel<Unit>(Channel.BUFFERED)
        private val deleted = Channel<DeletedEnvelope>(Channel.BUFFERED)

        // Categories the group had when the editor opened, for the delete undo snapshot.
        @Volatile
        private var originalMembers: Set<CategoryId> = emptySet()

        /** One-shot messages for writes that failed, shown as a snackbar. */
        val errorEvent: Flow<UiText> = errors.receiveAsFlow()

        /** Emits once after a successful save. */
        val navigateBack: Flow<Unit> = back.receiveAsFlow()

        /** Emits once after a successful delete, with what the app-level undo needs. */
        val deletedEvent: Flow<DeletedEnvelope> = deleted.receiveAsFlow()

        val uiState: StateFlow<EnvelopeEditorUiState> =
            combine(
                draft,
                combine(
                    envelopeRepository.observeEnvelopes(),
                    categoryRepository.observeCategories(),
                    ::EditorData,
                ),
                combine(notFound, submitted, saving, ::EditorFlags),
                ::stateOf,
            ).stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                EnvelopeEditorUiState.Loading,
            )

        init {
            viewModelScope.launch { seed() }
        }

        private suspend fun seed() {
            runCatching {
                val envelopes = envelopeRepository.observeEnvelopes().first()
                val categories = categoryRepository.observeCategories().first()
                val envelope = envelopeId?.let { id -> envelopes.firstOrNull { it.id == id } }
                if (envelopeId != null && envelope == null) {
                    notFound.value = true
                } else {
                    val seeded = draftOf(envelope, categories)
                    originalMembers = seeded.categoryIds.toSet()
                    draft.value = seeded
                }
            }.onFailure {
                Log.w(TAG, "Failed to load the envelope", it)
                notFound.value = true
            }
        }

        private fun stateOf(
            current: EnvelopeDraft?,
            data: EditorData,
            flags: EditorFlags,
        ): EnvelopeEditorUiState {
            if (current == null) {
                return if (flags.notFound) EnvelopeEditorUiState.NotFound else EnvelopeEditorUiState.Loading
            }
            val others = data.envelopes.filter { it.id != current.id }
            val error = if (flags.submitted) validateEnvelope(current, others.map { it.name }) else null
            val byId = data.categories.associateBy { it.id }
            val envelopeNames = data.envelopes.associate { it.id to it.name }
            return EnvelopeEditorUiState.Content(
                draft = current,
                nameError =
                    when (error) {
                        EnvelopeDraftError.BlankName -> UiText.StringResource(R.string.envelope_error_name_blank)
                        EnvelopeDraftError.DuplicateName ->
                            UiText.StringResource(R.string.envelope_error_name_duplicate)
                        else -> null
                    },
                members =
                    current.categoryIds.mapNotNull { id ->
                        byId[id]?.let { category ->
                            val from = category.groupId?.takeIf { group -> group != current.id }
                            MemberUi(category, from?.let(envelopeNames::get))
                        }
                    },
                addable = addableOf(current, data),
                isNew = current.id == null,
                saving = flags.saving,
            )
        }

        private fun addableOf(
            current: EnvelopeDraft,
            data: EditorData,
        ): List<AddableCategoryUi> {
            val names = data.envelopes.associate { it.id to it.name }
            return data.categories
                .filter { !it.archived && it.kind != CategoryKind.SAVING && it.id !in current.categoryIds }
                .map { AddableCategoryUi(it, it.groupId?.takeIf { group -> group != current.id }?.let(names::get)) }
        }

        fun onNameChange(name: String) = edit { it.copy(name = limitInput(it.name, name, MAX_GROUP_NAME_LENGTH)) }

        fun onAddCategory(id: CategoryId) =
            edit {
                if (id in it.categoryIds) {
                    it
                } else {
                    it.copy(categoryIds = it.categoryIds + id)
                }
            }

        fun onRemoveCategory(id: CategoryId) {
            edit { it.copy(categoryIds = it.categoryIds - id) }
        }

        private fun edit(transform: (EnvelopeDraft) -> EnvelopeDraft) = draft.update { it?.let(transform) }

        /** Validates the draft and writes it in one save; navigates back on success. */
        fun onSave() {
            val current = draft.value ?: return
            if (saving.value) return
            submitted.value = true
            viewModelScope.launch {
                val others = envelopeRepository.observeEnvelopes().first().filter { it.id != current.id }
                if (validateEnvelope(current, others.map { it.name }) != null) return@launch
                saving.value = true
                envelopeRepository
                    .saveEnvelope(envelopeEditOf(current))
                    .onSuccess { back.send(Unit) }
                    .onFailure {
                        Log.w(TAG, "Failed to save the envelope", it)
                        errors.send(UiText.StringResource(R.string.error_group_save_failed))
                    }
                saving.value = false
            }
        }

        /** Deletes the envelope (the caller has confirmed) and emits what the app-level undo needs. */
        fun onDelete() {
            val id = draft.value?.id ?: return
            viewModelScope.launch {
                val envelopes = envelopeRepository.observeEnvelopes().first()
                val envelope = envelopes.firstOrNull { it.id == id } ?: return@launch
                val members = originalMembers
                val snapshot =
                    DeletedEnvelope(
                        edit =
                            EnvelopeEdit(
                                id = id,
                                name = envelope.name,
                                amountCents = envelope.amountCents,
                                fundedByLineId = envelope.fundedByLineId,
                                categoryIds = members,
                                limits = emptyMap(),
                            ),
                        position = envelopes.indexOfFirst { it.id == id },
                    )
                envelopeRepository
                    .deleteEnvelope(id)
                    .onSuccess { deleted.send(snapshot) }
                    .onFailure {
                        Log.w(TAG, "Failed to delete the envelope", it)
                        errors.send(UiText.StringResource(R.string.error_group_save_failed))
                    }
            }
        }
    }

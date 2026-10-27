package com.joaoeoneves.fintrack.ui.plan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.SuggestedGroup
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.repository.CategoryGroupRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.settings.categories.MoveDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "CategoryGroupsViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** The localized names of the [SuggestedGroup]s, resolved by the UI so the ViewModel needs no resources. */
data class SuggestedGroupNames(
    val recurring: String,
    val investments: String,
    val food: String,
    val other: String,
) {
    fun of(group: SuggestedGroup): String =
        when (group) {
            SuggestedGroup.RECURRING -> recurring
            SuggestedGroup.INVESTMENTS -> investments
            SuggestedGroup.FOOD -> food
            SuggestedGroup.OTHER -> other
        }
}

/** Manages the category groups of the Plan tab: add, rename, reorder, delete and the suggested starter set. */
@HiltViewModel
class CategoryGroupsViewModel
    @Inject
    constructor(
        private val groupRepository: CategoryGroupRepository,
        private val categoryRepository: CategoryRepository,
    ) : ViewModel() {
        private val errors = Channel<UiText>(Channel.BUFFERED)

        /** One-shot user-facing messages for group writes that failed, shown as a snackbar. */
        val errorEvent: Flow<UiText> = errors.receiveAsFlow()

        /** The groups in order; `null` until the first load. */
        val groups: StateFlow<List<CategoryGroup>?> =
            groupRepository
                .observeGroups()
                .catch {
                    Log.w(TAG, "Failed to observe the category groups", it)
                    emit(emptyList())
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), null)

        fun onAdd(name: String) {
            val trimmed = name.trim()
            if (!isValidName(trimmed)) return
            write { groupRepository.addGroup(trimmed) }
        }

        fun onRename(
            id: String,
            name: String,
        ) {
            val trimmed = name.trim()
            if (!isValidName(trimmed)) return
            write { groupRepository.renameGroup(id, trimmed) }
        }

        fun onMove(
            id: String,
            direction: MoveDirection,
        ) {
            val ids = groups.value.orEmpty().map { it.id }
            val index = ids.indexOf(id)
            val target = if (direction == MoveDirection.UP) index - 1 else index + 1
            if (index < 0 || target !in ids.indices) return
            val reordered = ids.toMutableList().apply { add(target, removeAt(index)) }
            write { groupRepository.reorderGroups(reordered) }
        }

        fun onDelete(id: String) {
            write { groupRepository.deleteGroup(id) }
        }

        /** Creates the [SuggestedGroup]s and files their built-in categories; does nothing once groups exist. */
        fun onCreateSuggested(names: SuggestedGroupNames) {
            write {
                runCatching {
                    check(groupRepository.observeGroups().first().isEmpty()) { "Groups already exist" }
                    SuggestedGroup.entries.forEach { suggested ->
                        val id = groupRepository.addGroup(names.of(suggested)).getOrThrow()
                        suggested.categoryIds.forEach { categoryRepository.setGroup(it, id).getOrThrow() }
                    }
                }
            }
        }

        private fun isValidName(name: String) = name.isNotEmpty() && name.codePointLength() <= MAX_GROUP_NAME_LENGTH

        private fun write(block: suspend () -> Result<*>) {
            viewModelScope.launch {
                block().onFailure {
                    Log.w(TAG, "Failed to save the category groups", it)
                    errors.send(UiText.StringResource(R.string.error_group_save_failed))
                }
            }
        }
    }

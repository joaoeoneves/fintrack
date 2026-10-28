package com.joaoeoneves.fintrack.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.preferences.StartChecklistStore
import com.joaoeoneves.fintrack.domain.repository.DataResetRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "DeleteAllDataViewModel"

/** Where the "Delete all data" flow is: asking, working, or showing a retryable failure. */
sealed interface DeleteAllDataState {
    data object Idle : DeleteAllDataState

    data object Confirming : DeleteAllDataState

    data object Deleting : DeleteAllDataState

    data class Failed(
        val message: UiText,
    ) : DeleteAllDataState
}

/** Drives the confirm / progress / error dialogs of "Delete all data" and reports [deleted] on success. */
@HiltViewModel
class DeleteAllDataViewModel
    @Inject
    constructor(
        private val dataResetRepository: DataResetRepository,
        private val startChecklistStore: StartChecklistStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow<DeleteAllDataState>(DeleteAllDataState.Idle)
        val state: StateFlow<DeleteAllDataState> = _state.asStateFlow()

        private val deletedEvents = Channel<Unit>(Channel.BUFFERED)

        /** Emits once each time all data was deleted successfully. */
        val deleted: Flow<Unit> = deletedEvents.receiveAsFlow()

        fun onRequestDelete() {
            if (_state.value == DeleteAllDataState.Idle) _state.value = DeleteAllDataState.Confirming
        }

        /** Dismisses the confirmation or the error; ignored while deleting. */
        fun onDismiss() {
            if (_state.value != DeleteAllDataState.Deleting) _state.value = DeleteAllDataState.Idle
        }

        /** Starts the deletion from the confirmation, or runs it again from a failure (finishing a partial wipe). */
        fun onConfirmDelete() {
            if (_state.value !is DeleteAllDataState.Confirming && _state.value !is DeleteAllDataState.Failed) return
            _state.value = DeleteAllDataState.Deleting
            viewModelScope.launch {
                dataResetRepository.deleteAllData().fold(
                    onSuccess = {
                        startChecklistStore.clear()
                        _state.value = DeleteAllDataState.Idle
                        deletedEvents.send(Unit)
                    },
                    onFailure = {
                        Log.w(TAG, "Delete all data failed", it)
                        _state.value =
                            DeleteAllDataState.Failed(UiText.StringResource(R.string.delete_all_data_error))
                    },
                )
            }
        }
    }

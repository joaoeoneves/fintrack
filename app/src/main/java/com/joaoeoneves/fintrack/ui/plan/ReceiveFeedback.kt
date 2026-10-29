package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.joaoeoneves.fintrack.ui.designsystem.showActionSnackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the "Change" action of the received snackbar reopens: the sheet for one income row. */
data class ReceiveChange(
    val title: String,
    val today: LocalDate,
    val editableAmountCents: Long?,
    val onConfirm: (date: LocalDate, amountCents: Long?) -> Unit,
    /** The day the sheet starts on: the row's pay date, never after [today]. */
    val date: LocalDate = today,
)

/**
 * Screen-level owner of the "Received on ..." snackbar and the sheet its "Change" action opens. It lives above the
 * income rows because a row can leave the list (the dashboard hides received rows) right after being ticked.
 */
class ReceiveFeedback(
    private val host: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    var change by mutableStateOf<ReceiveChange?>(null)
        private set

    /** Shows [message] with [undoLabel]; tapping the action runs [onUndo]. */
    fun announce(
        message: String,
        undoLabel: String,
        onUndo: () -> Unit,
    ) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            if (host.showActionSnackbar(message, undoLabel) == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    /** Opens the receive sheet for [change] (the row's "Alterar valor"). */
    fun open(change: ReceiveChange) {
        this.change = change
    }

    fun dismissChange() {
        change = null
    }
}

/** The enclosing screen's [ReceiveFeedback]; `null` outside such a screen, where ticking shows no snackbar. */
val LocalReceiveFeedback = staticCompositionLocalOf<ReceiveFeedback?> { null }

@Composable
fun rememberReceiveFeedback(host: SnackbarHostState): ReceiveFeedback {
    val scope = rememberCoroutineScope()
    return remember(host, scope) { ReceiveFeedback(host, scope) }
}

/** The receive sheet opened by a row's "Alterar valor", when there is one. */
@Composable
fun ReceiveFeedback.ChangeSheet() {
    change?.let { current ->
        ReceivedIncomeSheet(
            title = current.title,
            today = current.today,
            initialDate = current.date,
            editableAmountCents = current.editableAmountCents,
            onConfirm = { date, cents ->
                current.onConfirm(date, cents)
                dismissChange()
            },
            onDismiss = ::dismissChange,
        )
    }
}

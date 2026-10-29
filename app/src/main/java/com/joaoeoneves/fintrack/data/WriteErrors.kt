package com.joaoeoneves.fintrack.data

import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.OnFailureListener
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Why a write the server rejected failed, as a code the UI shows a fixed localised message for (never raw text). */
enum class WriteErrorCode { PERMISSION_DENIED, OTHER }

/**
 * App-wide channel for Firestore writes the server rejected after the app already reported success (writes are not
 * awaited, so a rules rejection only shows up later). The main scaffold shows each as a snackbar. Carries a
 * [WriteErrorCode] only; the raw text goes to the log. The same code repeated within [DEDUPE_WINDOW_MS] is emitted
 * once (a batch of rejected writes is one message).
 */
object WriteErrors {
    private val mutableEvents =
        MutableSharedFlow<WriteErrorCode>(
            extraBufferCapacity = EVENT_BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val lastEmitted = HashMap<WriteErrorCode, Long>()

    /** The code of each rejected write, repeats collapsed. */
    val events: SharedFlow<WriteErrorCode> = mutableEvents

    internal fun emit(
        error: Throwable,
        now: Long = SystemClock.elapsedRealtime(),
    ) {
        val code =
            if ((error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                WriteErrorCode.PERMISSION_DENIED
            } else {
                WriteErrorCode.OTHER
            }
        val fresh =
            synchronized(lastEmitted) {
                val last = lastEmitted[code]
                (last == null || now - last >= DEDUPE_WINDOW_MS).also { if (it) lastEmitted[code] = now }
            }
        if (fresh) mutableEvents.tryEmit(code)
    }
}

private const val EVENT_BUFFER = 8
private const val DEDUPE_WINDOW_MS = 5_000L

/**
 * A failure listener for a not-awaited write: logs it and tells the user, unless it only failed offline. With
 * [notify] false (automatic background writes) it only logs: the user did not ask for them, a repeating toast helps
 * nobody.
 */
internal fun onFail(
    tag: String,
    message: String,
    notify: Boolean = true,
) = OnFailureListener { error ->
    Log.w(tag, message, error)
    val offline = (error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.UNAVAILABLE
    if (notify && !offline) WriteErrors.emit(error)
}

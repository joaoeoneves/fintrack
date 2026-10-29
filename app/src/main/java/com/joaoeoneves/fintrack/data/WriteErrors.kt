package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.android.gms.tasks.OnFailureListener
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * App-wide channel for Firestore writes the server rejected after the app already reported success (writes are not
 * awaited, so a rules rejection only shows up later). The main scaffold shows each as a snackbar.
 */
object WriteErrors {
    private val mutableEvents =
        MutableSharedFlow<String>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The reason of each rejected write. */
    val events: SharedFlow<String> = mutableEvents

    internal fun emit(error: Throwable) {
        mutableEvents.tryEmit(error.message ?: error.javaClass.simpleName)
    }
}

private const val EVENT_BUFFER = 8

/** A failure listener for a not-awaited write: logs it and tells the user, unless it only failed offline. */
internal fun onFail(
    tag: String,
    message: String,
) = OnFailureListener { error ->
    Log.w(tag, message, error)
    val offline = (error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.UNAVAILABLE
    if (!offline) WriteErrors.emit(error)
}

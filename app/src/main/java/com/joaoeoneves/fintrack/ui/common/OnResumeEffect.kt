package com.joaoeoneves.fintrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate

/**
 * Invokes [onResume] when the hosting lifecycle reaches ON_RESUME and the calendar day (in the
 * device time zone) differs from the one seen at the previous resume, so date-relative periods
 * such as "This month" re-resolve after a midnight rollover without re-attaching listeners on
 * every resume.
 */
@Composable
fun OnResumeEffect(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume by rememberUpdatedState(onResume)
    DisposableEffect(lifecycleOwner) {
        var lastDate = LocalDate.now()
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val today = LocalDate.now()
                    if (today != lastDate) {
                        lastDate = today
                        currentOnResume()
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

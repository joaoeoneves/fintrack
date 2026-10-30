package com.joaoeoneves.fintrack.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity

/** The [Activity] behind this context, unwrapping any [ContextWrapper]s, or `null`. */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** The [FragmentActivity] behind this context, or `null`. */
internal fun Context.findFragmentActivity(): FragmentActivity? = findActivity() as? FragmentActivity

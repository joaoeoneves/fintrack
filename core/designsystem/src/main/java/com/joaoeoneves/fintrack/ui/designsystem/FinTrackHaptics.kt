package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Semantic haptics shared across screens. Haptics are tactile, not decorative, so they are deliberately not gated
 * by reduced motion.
 */
@Immutable
class FinTrackHaptics(
    private val feedback: HapticFeedback,
) {
    /** A user action was accepted (confirm, receive, delete). */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** A user action was declined (skip, dismiss). */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)

    /** A light tick, e.g. a gesture threshold being crossed. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

    /** A milestone was reached (goal completed, payday received). */
    fun success() = feedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
}

@Composable
fun rememberFinTrackHaptics(): FinTrackHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { FinTrackHaptics(feedback) }
}

package com.joaoeoneves.fintrack.ui.lock

/** How a system authentication prompt ended. */
enum class LockPromptResult {
    /** The user authenticated. */
    SUCCESS,

    /** The user dismissed the prompt, or it was interrupted; nothing changes. */
    CANCELLED,

    /** The device cannot authenticate (no screen lock, hardware unavailable): never lock the user out. */
    UNAVAILABLE,
}

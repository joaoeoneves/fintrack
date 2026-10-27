package com.joaoeoneves.fintrack.domain.model

/**
 * Whether money leaving under an [Category] is consumed ([SPENDING]) or set aside
 * ([SAVING], e.g. investments). Both count as outflow, but only [SPENDING] is "spent".
 */
enum class CategoryKind {
    SPENDING,
    SAVING,
}

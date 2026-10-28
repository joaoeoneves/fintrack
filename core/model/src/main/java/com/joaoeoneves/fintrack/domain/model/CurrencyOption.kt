package com.joaoeoneves.fintrack.domain.model

/**
 * User-selectable display currency. Purely cosmetic — switching currency only changes the printed
 * symbol on the existing stored cent value; there is no exchange-rate conversion and no
 * per-item currency tagging in Firestore.
 */
enum class CurrencyOption {
    USD,
    EUR,
    GBP,
    JPY,
    CAD,
    AUD,
}

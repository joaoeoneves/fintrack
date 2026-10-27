package com.joaoeoneves.fintrack.domain.model

// Mirror the size limits enforced by firestore.rules for expense/income documents, so the UI and
// CSV import never produce a write the server would reject.

/** Maximum length, in code points, of an expense name or income source. */
const val MAX_NAME_LENGTH = 200

/** Maximum length, in code points, of a user-defined category name. */
const val MAX_CATEGORY_NAME_LENGTH = 50

/** Maximum length, in code points, of a category group name. */
const val MAX_GROUP_NAME_LENGTH = 50

/** Maximum length, in code points, of an expense or income note. */
const val MAX_NOTE_LENGTH = 2000

/** Maximum length, in code points, of a planned-income line label. */
const val MAX_PLAN_LABEL_LENGTH = 50

/** Largest money amount accepted, in cents (1 billion); mirrors the upper bound in `firestore.rules`. */
const val MAX_AMOUNT_CENTS = 100_000_000_000L

/** Maximum number of planned-income lines (enforced client-side only; rules can't count documents). */
const val MAX_PLANNED_INCOME_LINES = 20

/** Most working days a month can be set to (the longest possible Monday to Friday count). */
const val MAX_WORKING_DAYS = 23

/** Most days off a single month can hold; mirrors the bound in `firestore.rules`. */
const val MAX_DAYS_OFF = 23

// The length counter appears once a field is more than 4/5 (80%) of the way to its limit.
private const val COUNTER_THRESHOLD_NUMERATOR = 4
private const val COUNTER_THRESHOLD_DENOMINATOR = 5

/** Length in Unicode code points (what Firestore rules' `size()` counts), not UTF-16 chars. */
fun String.codePointLength(): Int = codePointCount(0, length)

/**
 * Applies a [max] code-point length limit to a text-field edit. Returns [proposed] when it fits;
 * otherwise keeps [current] if it is already at (or over) the limit, so further typing is a no-op,
 * or else [proposed] truncated to [max] code points (e.g. a paste that overshoots).
 */
fun limitInput(
    current: String,
    proposed: String,
    max: Int,
): String =
    when {
        proposed.codePointLength() <= max -> proposed
        current.codePointLength() >= max -> current
        else -> proposed.substring(0, proposed.offsetByCodePoints(0, max))
    }

/** Whether a field of [length] should show its "length/max" counter (above 80% of [max]). */
fun shouldShowLengthCounter(
    length: Int,
    max: Int,
): Boolean = length * COUNTER_THRESHOLD_DENOMINATOR > max * COUNTER_THRESHOLD_NUMERATOR

package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.ui.common.FALLBACK_COLOR_KEY
import com.joaoeoneves.fintrack.ui.common.categoryColorKeys

/**
 * The colour key an envelope is drawn with: the colour of its first category in [categories] order (so the
 * envelope looks like what is inside it everywhere), or slate for the "no envelope" bucket / an empty envelope.
 */
fun envelopeColorKeyOf(
    envelopeId: String,
    categories: List<Category>,
): String =
    if (envelopeId == NO_ENVELOPE) {
        FALLBACK_COLOR_KEY
    } else {
        categories.firstOrNull { it.groupId == envelopeId }?.colorKey ?: FALLBACK_COLOR_KEY
    }

/**
 * [envelopeColorKeyOf] for every id in [envelopeIds] (in order), resolving collisions: an envelope whose key was
 * already taken by an earlier one gets the next palette colour (slate excluded) not used yet.
 */
fun envelopeColorKeysOf(
    envelopeIds: List<String>,
    categories: List<Category>,
): List<String> {
    val palette = categoryColorKeys.filter { it != FALLBACK_COLOR_KEY }
    val used = mutableSetOf<String>()
    return envelopeIds.map { id ->
        val own = envelopeColorKeyOf(id, categories)
        val key =
            if (own == FALLBACK_COLOR_KEY || own !in used) own else palette.firstOrNull { it !in used } ?: own
        used += key
        key
    }
}

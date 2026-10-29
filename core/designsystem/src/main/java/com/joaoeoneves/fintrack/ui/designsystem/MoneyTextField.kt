@file:Suppress("MatchingDeclarationName") // the field is the main declaration; the transformation supports it

package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.ui.common.MoneySymbols
import com.joaoeoneves.fintrack.ui.common.applyCentsEdit
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatCentsGrouped
import com.joaoeoneves.fintrack.ui.common.moneySymbolsFor
import com.joaoeoneves.fintrack.ui.common.normalizeDecimalInput
import com.joaoeoneves.fintrack.ui.common.toCentsRaw

/** Shows the raw digits as a grouped amount; the cursor is always pinned to the end. */
class MoneyVisualTransformation(
    private val symbols: MoneySymbols,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // Empty input shows nothing so the field's grey "0,00" placeholder is the only zero drawn.
        if (text.text.isEmpty()) return TransformedText(AnnotatedString(""), OffsetMapping.Identity)
        val cents = text.text.toLongOrNull() ?: 0L
        val shown = formatCentsGrouped(cents, symbols)
        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = shown.length

                override fun transformedToOriginal(offset: Int): Int = text.length
            }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

/** A cash-register money field: the user types digits only, the app places the separators. */
@Suppress("LongParameterList") // mirrors the text-field parameters it wraps
@Composable
fun MoneyTextField(
    cents: Long,
    onCentsChange: (Long) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    maxCents: Long = MAX_AMOUNT_CENTS,
    emptyText: String? = null,
) {
    val locale = currentLocale()
    val symbols = remember(locale) { moneySymbolsFor(locale) }
    val transformation = remember(symbols) { MoneyVisualTransformation(symbols) }
    val raw = cents.toCentsRaw()
    val spoken = formatCentsGrouped(cents, symbols)
    MinimalTextField(
        value = TextFieldValue(raw, selection = TextRange(raw.length)),
        onValueChange = { onCentsChange(applyCentsEdit(cents, it.text, maxCents)) },
        label = label,
        visualTransformation = transformation,
        isError = isError,
        placeholder = { Text(emptyText ?: formatCentsGrouped(0L, symbols)) },
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
        modifier =
            modifier.semantics {
                contentDescription = label
                stateDescription = spoken
            },
    )
}

/** A text field for percent/multiplier values, normalised to the app language's decimal separator. */
@Suppress("LongParameterList") // mirrors the text-field parameters it wraps
@Composable
fun DecimalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    val locale = currentLocale()
    val decimal = remember(locale) { moneySymbolsFor(locale).decimal }
    MinimalTextField(
        value = value,
        onValueChange = { onValueChange(normalizeDecimalInput(it, decimal)) },
        label = label,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

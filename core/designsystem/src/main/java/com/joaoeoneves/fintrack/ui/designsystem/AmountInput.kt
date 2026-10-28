package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaoeoneves.fintrack.ui.common.applyCentsEdit
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatCentsGrouped
import com.joaoeoneves.fintrack.ui.common.moneySymbolsFor
import com.joaoeoneves.fintrack.ui.common.toCentsRaw
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.InterFamily

private val AmountTextStyle =
    TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 52.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = "tnum",
    )

private val AmountMinWidth = 40.dp

/**
 * The large borderless amount entry of the add/edit screens: a centred currency symbol (before or
 * after the text, per [symbolLeads]) around the raw text the user typed. [label] is exposed as the
 * field's content description so tests and screen readers can find it, since no visible label is
 * shown.
 */
@Suppress("LongParameterList") // mirrors the text-field parameters it wraps
@Composable
fun BorderlessAmountField(
    cents: Long,
    onCentsChange: (Long) -> Unit,
    currencySymbol: String,
    symbolLeads: Boolean,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val locale = currentLocale()
    val symbols = remember(locale) { moneySymbolsFor(locale) }
    val transformation = remember(symbols) { MoneyVisualTransformation(symbols) }
    val raw = cents.toCentsRaw()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }
    val contentColor = if (isError) FinTrackTheme.colors.negative else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (symbolLeads) CurrencySymbolText(currencySymbol, Modifier.padding(end = 8.dp))
        BasicTextField(
            value = TextFieldValue(raw, selection = TextRange(raw.length)),
            onValueChange = { onCentsChange(applyCentsEdit(cents, it.text)) },
            visualTransformation = transformation,
            singleLine = true,
            textStyle = AmountTextStyle.copy(color = contentColor),
            cursorBrush = SolidColor(if (isError) contentColor else FinTrackTheme.colors.accentText),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            decorationBox = { innerTextField ->
                Box {
                    if (raw.isEmpty()) {
                        Text(
                            text = formatCentsGrouped(0L, symbols),
                            style = AmountTextStyle,
                            color = FinTrackTheme.colors.textSecondary,
                        )
                    }
                    innerTextField()
                }
            },
            modifier =
                Modifier
                    .widthIn(min = AmountMinWidth)
                    .width(IntrinsicSize.Min)
                    .focusRequester(focusRequester)
                    .semantics {
                        contentDescription = label
                        stateDescription = formatCentsGrouped(cents, symbols)
                    },
        )
        if (!symbolLeads) CurrencySymbolText(currencySymbol, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun CurrencySymbolText(
    symbol: String,
    modifier: Modifier,
) {
    Text(text = symbol, style = AmountTextStyle, color = FinTrackTheme.colors.textSecondary, modifier = modifier)
}

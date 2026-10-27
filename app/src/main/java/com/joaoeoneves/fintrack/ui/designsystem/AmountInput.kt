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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * The large borderless amount entry of the add/edit screens: a centred currency symbol followed
 * by the raw text the user typed. [label] is exposed as the field's content description so tests
 * and screen readers can find it, since no visible label is shown.
 */
@Suppress("LongParameterList") // mirrors the text-field parameters it wraps
@Composable
fun BorderlessAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    currencySymbol: String,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
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
        Text(
            text = currencySymbol,
            style = AmountTextStyle,
            color = FinTrackTheme.colors.textSecondary,
            modifier = Modifier.padding(end = 8.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = AmountTextStyle.copy(color = contentColor),
            cursorBrush = SolidColor(contentColor),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(text = "0", style = AmountTextStyle, color = FinTrackTheme.colors.textSecondary)
                    }
                    innerTextField()
                }
            },
            modifier =
                Modifier
                    .widthIn(min = AmountMinWidth)
                    .width(IntrinsicSize.Min)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = label },
        )
    }
}

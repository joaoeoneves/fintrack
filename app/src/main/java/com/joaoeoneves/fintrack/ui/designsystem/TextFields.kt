package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/**
 * A filled text field on the soft surface tone with no underline indicator. Keeps a real `label`
 * slot so the label doubles as the accessible name and as the tap target in UI flows.
 */
@Suppress("LongParameterList") // mirrors the subset of TextField parameters the app uses
@Composable
fun MinimalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val surface2 = FinTrackTheme.colors.surface2
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        placeholder = placeholder,
        supportingText = supportingText,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        shape = MaterialTheme.shapes.medium,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = surface2,
                unfocusedContainerColor = surface2,
                errorContainerColor = surface2,
                disabledContainerColor = surface2,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = FinTrackTheme.colors.textSecondary,
                unfocusedLabelColor = FinTrackTheme.colors.textSecondary,
                errorLabelColor = FinTrackTheme.colors.negative,
            ),
        modifier = modifier,
    )
}

package com.joaoeoneves.fintrack.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.shouldShowLengthCounter

/**
 * A text field's supporting line: the validation [error] (if any) on the left and, once the field
 * nears its [max] length (see [shouldShowLengthCounter]), a right-aligned "length/max" counter.
 */
@Composable
fun FieldSupportingText(
    error: String?,
    length: Int,
    max: Int,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = error.orEmpty(), modifier = Modifier.weight(1f))
        if (shouldShowLengthCounter(length, max)) {
            Text(
                text = stringResource(R.string.field_length_counter, length, max),
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

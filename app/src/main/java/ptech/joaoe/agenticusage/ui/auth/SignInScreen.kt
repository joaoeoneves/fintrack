package ptech.joaoe.agenticusage.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ptech.joaoe.agenticusage.ui.theme.AgenticUsageTheme

@Composable
fun SignInScreen(
    uiState: AuthUiState,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLoading = uiState is AuthUiState.Loading

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AgenticUsage",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Sign in to track your expenses",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        if (uiState is AuthUiState.Error) {
            Text(
                text = uiState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Button(
            onClick = onSignInClick,
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = "Sign in with Google",
                    modifier = Modifier.padding(start = 8.dp)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoogleLogo(modifier = Modifier.size(18.dp))
                    Text(
                        text = "Sign in with Google",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * A small multi-color Google "G" mark, hand-drawn with [Canvas] since the project only depends on
 * material-icons-core (no material-icons-extended, which is where a bundled Google glyph would
 * otherwise come from).
 */
@Composable
private fun GoogleLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension / 4f
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)

        // Blue: right side
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -60f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )
        // Green: bottom
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 60f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )
        // Yellow: left-bottom
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 150f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )
        // Red: left-top
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 240f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )
        // Blue horizontal bar, the crossbar of the "G"
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width / 2f, size.height / 2f),
            end = Offset(topLeft.x + diameter, size.height / 2f),
            strokeWidth = strokeWidth
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SignInScreenPreview() {
    AgenticUsageTheme {
        SignInScreen(uiState = AuthUiState.Idle, onSignInClick = {})
    }
}

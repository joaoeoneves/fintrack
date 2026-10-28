package com.joaoeoneves.fintrack.ui.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.InterFamily
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion
import com.joaoeoneves.fintrack.ui.theme.Motion

private val GoogleLogoSize = 20.dp

@Composable
fun SignInScreen(
    uiState: AuthUiState,
    onSignInClick: () -> Unit,
    onContinueAsGuest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = uiState is AuthUiState.Loading
    val reducedMotion = LocalReducedMotion.current
    val alpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) alpha.snapTo(1f) else alpha.animateTo(1f, tween(Motion.Emphasis, easing = Motion.Standard))
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space24)
                .graphicsLayer { this.alpha = alpha.value },
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = WordmarkStyle,
            )
            Text(
                text = stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = FinTrackTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Dimens.Space8),
            )
        }

        if (uiState is AuthUiState.Error) {
            Text(
                text = uiState.message.asString(),
                color = FinTrackTheme.colors.negative,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.Space16),
            )
        }

        PrimaryPillButton(
            text = stringResource(R.string.auth_sign_in_with_google),
            onClick = onSignInClick,
            loading = isLoading,
            leadingIcon = { GoogleLogo(modifier = Modifier.size(GoogleLogoSize)) },
        )

        GuestEntry(onContinueAsGuest = onContinueAsGuest, enabled = !isLoading)
    }
}

private val WordmarkStyle =
    TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        letterSpacing = (-1).sp,
    )

@Composable
private fun GuestEntry(
    onContinueAsGuest: () -> Unit,
    enabled: Boolean,
) {
    GhostButton(
        text = stringResource(R.string.auth_continue_as_guest),
        onClick = onContinueAsGuest,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.Space12).testTag(TestTags.CONTINUE_AS_GUEST),
    )
    Text(
        text = stringResource(R.string.auth_guest_hint),
        style = MaterialTheme.typography.bodySmall,
        color = FinTrackTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.Space12),
    )
}

// The ratio of the mark's stroke width to its overall size: a quarter of the smallest dimension
// keeps the "G" ring visually proportioned like Google's published logo.
private const val STROKE_WIDTH_DIVISOR = 4f

/**
 * A small multi-color Google "G" mark, hand-drawn with [Canvas] since the project only depends on
 * material-icons-core (no material-icons-extended, which is where a bundled Google glyph would
 * otherwise come from).
 *
 * The four `Color(0xFF...)` literals below are Google's published brand colors for the "G" mark;
 * naming each one individually would just restate what this doc comment already documents, so
 * suppress at this composable rather than extract four one-off constants.
 */
@Suppress("MagicNumber")
@Composable
private fun GoogleLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension / STROKE_WIDTH_DIVISOR
        val diameter = size.minDimension - strokeWidth
        val topLeft =
            Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f,
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
            style = Stroke(width = strokeWidth),
        )
        // Green: bottom
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 60f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth),
        )
        // Yellow: left-bottom
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 150f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth),
        )
        // Red: left-top
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 240f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth),
        )
        // Blue horizontal bar, the crossbar of the "G"
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width / 2f, size.height / 2f),
            end = Offset(topLeft.x + diameter, size.height / 2f),
            strokeWidth = strokeWidth,
        )
    }
}

// Compose preview function: only rendered by Android Studio's preview pane, not dead code.
@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun SignInScreenPreview() {
    FinTrackTheme {
        SignInScreen(uiState = AuthUiState.Idle, onSignInClick = {}, onContinueAsGuest = {})
    }
}

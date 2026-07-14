package ptech.joaoe.agenticusage.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ptech.joaoe.agenticusage.domain.model.AuthUser
import ptech.joaoe.agenticusage.ui.theme.AgenticUsageTheme

/**
 * Placeholder post-sign-in screen. The real dashboard/navigation graph is a separate future feature.
 */
@Composable
fun SignedInScreen(
    user: AuthUser,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Signed in as ${user.email ?: user.displayName ?: user.uid}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(onClick = onSignOutClick) {
            Text("Sign out")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignedInScreenPreview() {
    AgenticUsageTheme {
        SignedInScreen(
            user = AuthUser(uid = "uid", displayName = "Jane Doe", email = "jane@example.com", photoUrl = null),
            onSignOutClick = {}
        )
    }
}

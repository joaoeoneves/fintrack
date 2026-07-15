package ptech.joaoe.agenticusage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ptech.joaoe.agenticusage.domain.model.ThemePreference
import ptech.joaoe.agenticusage.ui.auth.AuthUiState
import ptech.joaoe.agenticusage.ui.auth.AuthViewModel
import ptech.joaoe.agenticusage.ui.auth.SignInScreen
import ptech.joaoe.agenticusage.ui.navigation.AgenticUsageNavHost
import ptech.joaoe.agenticusage.ui.theme.AgenticUsageTheme
import ptech.joaoe.agenticusage.ui.theme.ThemeViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themePreference by themeViewModel.themePreference.collectAsStateWithLifecycle()
            val resolvedDarkTheme =
                when (themePreference) {
                    ThemePreference.SYSTEM -> isSystemInDarkTheme()
                    ThemePreference.LIGHT -> false
                    ThemePreference.DARK -> true
                }

            AgenticUsageTheme(darkTheme = resolvedDarkTheme, dynamicColor = false) {
                val viewModel: AuthViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val state = uiState
                    if (state is AuthUiState.SignedIn) {
                        AgenticUsageNavHost(
                            onSignOut = viewModel::signOut,
                            isDarkTheme = resolvedDarkTheme,
                            onToggleTheme = { themeViewModel.toggleTheme(resolvedDarkTheme) },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        SignInScreen(
                            uiState = state,
                            onSignInClick = { viewModel.signIn(context) },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

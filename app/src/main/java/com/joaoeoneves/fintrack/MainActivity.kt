package com.joaoeoneves.fintrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.ui.auth.AuthUiState
import com.joaoeoneves.fintrack.ui.auth.AuthViewModel
import com.joaoeoneves.fintrack.ui.auth.SignInScreen
import com.joaoeoneves.fintrack.ui.common.LocalCurrency
import com.joaoeoneves.fintrack.ui.navigation.FinTrackNavHost
import com.joaoeoneves.fintrack.ui.theme.CurrencyViewModel
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

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

            FinTrackTheme(darkTheme = resolvedDarkTheme, dynamicColor = false) {
                val viewModel: AuthViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val state = uiState
                    if (state is AuthUiState.SignedIn) {
                        val currencyViewModel: CurrencyViewModel = hiltViewModel()
                        val currency by currencyViewModel.currency.collectAsStateWithLifecycle()
                        CompositionLocalProvider(LocalCurrency provides currency) {
                            FinTrackNavHost(
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
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

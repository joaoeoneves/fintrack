package com.joaoeoneves.fintrack

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.LanguageRepository
import com.joaoeoneves.fintrack.ui.auth.AuthUiState
import com.joaoeoneves.fintrack.ui.auth.AuthViewModel
import com.joaoeoneves.fintrack.ui.auth.SignInScreen
import com.joaoeoneves.fintrack.ui.common.LocalCurrency
import com.joaoeoneves.fintrack.ui.navigation.FinTrackNavHost
import com.joaoeoneves.fintrack.ui.theme.CurrencyViewModel
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Extends AppCompatActivity (rather than plain ComponentActivity) because
// AppCompatDelegate.setApplicationLocales — used by SharedPrefsLanguageRepository to drive the
// in-app language picker — is documented to require an AppCompatActivity host when the UI is
// built with Compose; without it, setApplicationLocales silently fails to recreate/relocalize the
// activity. AppCompatActivity extends ComponentActivity, so `setContent {}` still works unchanged.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var languageRepository: LanguageRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AppCompatDelegate.setApplicationLocales() is a no-op until at least one
        // AppCompatActivity has been created, so the persisted/default language can only be
        // reliably (re-)applied here, not earlier (e.g. from Application.onCreate()) -- confirmed
        // via on-device testing: an Application-level attempt left AppCompatDelegate reporting an
        // empty locale list despite the call executing successfully.
        languageRepository.applyPersistedLocale()
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

                // A plain Surface rather than a Scaffold: every signed-in screen has its own
                // Scaffold that already consumes the system-bar insets, so an outer Scaffold here
                // would apply them twice (double top/bottom padding).
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val state = uiState
                    if (state is AuthUiState.SignedIn) {
                        val currencyViewModel: CurrencyViewModel = hiltViewModel()
                        val currency by currencyViewModel.currency.collectAsStateWithLifecycle()
                        CompositionLocalProvider(LocalCurrency provides currency) {
                            FinTrackNavHost(modifier = Modifier.fillMaxSize())
                        }
                    } else {
                        SignInScreen(
                            uiState = state,
                            onSignInClick = { viewModel.signIn(context) },
                            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                        )
                    }
                }
            }
        }
    }
}

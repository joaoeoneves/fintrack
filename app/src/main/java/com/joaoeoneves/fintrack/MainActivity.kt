package com.joaoeoneves.fintrack

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.LanguageRepository
import com.joaoeoneves.fintrack.ui.auth.AuthUiState
import com.joaoeoneves.fintrack.ui.auth.AuthViewModel
import com.joaoeoneves.fintrack.ui.auth.SignInScreen
import com.joaoeoneves.fintrack.ui.common.LocalCurrency
import com.joaoeoneves.fintrack.ui.common.applyAppLanguage
import com.joaoeoneves.fintrack.ui.migration.LocalDataMigrationHost
import com.joaoeoneves.fintrack.ui.navigation.FinTrackNavHost
import com.joaoeoneves.fintrack.ui.theme.CurrencyViewModel
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.NightModeSync
import com.joaoeoneves.fintrack.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

// Extends AppCompatActivity (rather than plain ComponentActivity) because
// AppCompatDelegate.setApplicationLocales -- used by applyAppLanguage to drive the in-app language
// picker -- is documented to require an AppCompatActivity host when the UI is built with Compose;
// without it, setApplicationLocales silently fails to recreate/relocalize the activity.
// AppCompatActivity extends ComponentActivity, so `setContent {}` still works unchanged.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var languageRepository: LanguageRepository

    // Shared with the hiltViewModel() lookups inside setContent (same activity store, same instances).
    private val themeViewModel: ThemeViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until the stored theme and the session have resolved, so the first frame
        // is the real UI rather than a blank one.
        keepSplashUntilResolved(splash)
        observeAppLanguage()
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themePreference by themeViewModel.themePreference.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            // Render nothing until the stored preference has loaded, to avoid a wrong-theme flash.
            val resolvedDarkTheme =
                when (themePreference) {
                    ThemePreference.SYSTEM -> systemDark
                    ThemePreference.LIGHT -> false
                    ThemePreference.DARK -> true
                    null -> null
                }

            LaunchedEffect(themePreference) { themePreference?.let { NightModeSync.apply(applicationContext, it) } }
            if (resolvedDarkTheme != null) {
                // Status/navigation bar icons follow the resolved app theme, not the system one.
                DisposableEffect(resolvedDarkTheme) {
                    val barStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { resolvedDarkTheme }
                    enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                    onDispose {}
                }
                FinTrackTheme(darkTheme = resolvedDarkTheme) {
                    val viewModel: AuthViewModel = hiltViewModel()
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val context = LocalContext.current

                    // A plain Surface rather than a Scaffold: every signed-in screen has its own
                    // Scaffold that already consumes the system-bar insets, so an outer Scaffold here
                    // would apply them twice (double top/bottom padding).
                    Surface(
                        modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        val state = uiState
                        if (state is AuthUiState.Starting) {
                            // Session still resolving: render nothing rather than flash the sign-in screen.
                            Unit
                        } else if (state is AuthUiState.SignedIn || state is AuthUiState.Guest) {
                            val currencyViewModel: CurrencyViewModel = hiltViewModel()
                            val currency by currencyViewModel.currency.collectAsStateWithLifecycle()
                            CompositionLocalProvider(LocalCurrency provides currency) {
                                FinTrackNavHost(modifier = Modifier.fillMaxSize())
                            }
                            // Only for accounts: guests have nothing to copy anywhere.
                            if (state is AuthUiState.SignedIn) LocalDataMigrationHost()
                        } else {
                            SignInScreen(
                                uiState = state,
                                onSignInClick = { viewModel.signIn(context) },
                                onContinueAsGuest = viewModel::continueAsGuest,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Holds the splash until the stored theme and the session have resolved, capped at
     * [SPLASH_MAX_MS] so a stuck Firestore/auth resolution can never trap the user on it.
     */
    private fun keepSplashUntilResolved(splash: SplashScreen) {
        var timedOut = false
        lifecycleScope.launch {
            delay(SPLASH_MAX_MS)
            timedOut = true
        }
        splash.setKeepOnScreenCondition {
            !timedOut &&
                (themeViewModel.themePreference.value == null || authViewModel.uiState.value is AuthUiState.Starting)
        }
    }

    /**
     * AppCompat restores its own persisted locale before the first frame; this keeps it in line
     * with the DataStore value (e.g. after a cloud restore) and is a no-op when they already match.
     */
    private fun observeAppLanguage() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                languageRepository.observeLanguage().collect(::applyAppLanguage)
            }
        }
    }
}

private const val SPLASH_MAX_MS = 1_500L

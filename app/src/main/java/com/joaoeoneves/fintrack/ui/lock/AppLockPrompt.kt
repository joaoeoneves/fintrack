package com.joaoeoneves.fintrack.ui.lock

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationResult
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private val UNAVAILABLE_ERRORS =
    setOf(
        BiometricPrompt.ERROR_HW_UNAVAILABLE,
        BiometricPrompt.ERROR_HW_NOT_PRESENT,
        BiometricPrompt.ERROR_NO_BIOMETRICS,
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
    )

/** Shows the system prompt (biometric or screen lock) and reports the outcome once through [onResult]. */
fun FragmentActivity.showAppLockPrompt(
    title: String,
    onResult: (LockPromptResult) -> Unit,
) {
    val callback =
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: AuthenticationResult) = onResult(LockPromptResult.SUCCESS)

            override fun onAuthenticationError(
                errorCode: Int,
                errString: CharSequence,
            ) = onResult(
                if (errorCode in UNAVAILABLE_ERRORS) LockPromptResult.UNAVAILABLE else LockPromptResult.CANCELLED,
            )
        }
    val info =
        BiometricPrompt.PromptInfo
            .Builder()
            .setTitle(title)
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
    BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(info)
}

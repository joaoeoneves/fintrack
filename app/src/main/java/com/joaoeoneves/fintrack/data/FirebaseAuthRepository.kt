package com.joaoeoneves.fintrack.data

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseAuthRepository
    @Inject
    constructor(
        private val firebaseAuth: FirebaseAuth,
        private val credentialManager: CredentialManager,
    ) : AuthRepository {
        override fun observeCurrentUser(): Flow<AuthUser?> =
            callbackFlow {
                val listener =
                    FirebaseAuth.AuthStateListener { auth ->
                        trySend(auth.currentUser?.toAuthUser())
                    }
                firebaseAuth.addAuthStateListener(listener)
                awaitClose { firebaseAuth.removeAuthStateListener(listener) }
            }

        override suspend fun signIn(context: Context): Result<AuthUser> {
            return try {
                val googleIdOption =
                    GetGoogleIdOption
                        .Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(context.getString(R.string.default_web_client_id))
                        .build()

                val request =
                    GetCredentialRequest
                        .Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                if (credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    return Result.failure(IllegalStateException("Unexpected credential type: ${credential.type}"))
                }

                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                val user =
                    authResult.user
                        ?: return Result.failure(IllegalStateException("Sign-in succeeded but no user was returned"))

                Result.success(user.toAuthUser())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        override suspend fun signOut(): Result<Unit> {
            val clearResult = runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            val signOutResult = runCatching { firebaseAuth.signOut() }
            clearResult.exceptionOrNull()?.let {
                Log.w(TAG, "Failed to clear credential state during sign-out", it)
            }
            return combineSignOutResults(clearResult, signOutResult)
        }

        private fun FirebaseUser.toAuthUser(): AuthUser =
            AuthUser(
                uid = uid,
                displayName = displayName,
                email = email,
                photoUrl = photoUrl?.toString(),
            )

        companion object {
            private const val TAG = "FirebaseAuthRepository"

            @Suppress("UnusedParameter")
            internal fun combineSignOutResults(
                clearCredentialResult: Result<Unit>,
                firebaseSignOutResult: Result<Unit>,
            ): Result<Unit> =
                firebaseSignOutResult.fold(
                    onSuccess = { Result.success(Unit) },
                    onFailure = { e -> Result.failure(e) },
                )
        }
    }

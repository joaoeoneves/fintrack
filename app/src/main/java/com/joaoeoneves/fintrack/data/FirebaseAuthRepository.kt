package com.joaoeoneves.fintrack.data

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.SignInCancelledException
import com.joaoeoneves.fintrack.domain.model.StarterCategoryNames
import com.joaoeoneves.fintrack.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
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
        private val firestore: FirebaseFirestore,
        private val starterNames: StarterCategoryNames,
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

        override suspend fun signIn(context: Context): Result<AuthUser> =
            try {
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

                when {
                    credential !is CustomCredential ||
                        credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                        Result.failure(IllegalStateException("Unexpected credential type: ${credential.type}"))
                    }
                    else -> {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                        val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                        val user = authResult.user
                        if (user != null && authResult.additionalUserInfo?.isNewUser == true) {
                            // Best effort and not awaited: a brand-new account starts with the starter categories.
                            runCatching { seedStarterCategories(firestore, user.uid, starterNames.names()) }
                                .onFailure { Log.w(TAG, "Could not seed the starter categories", it) }
                        }
                        if (user != null) {
                            Result.success(user.toAuthUser())
                        } else {
                            Result.failure(IllegalStateException("Sign-in succeeded but no user was returned"))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GetCredentialCancellationException) {
                // The user dismissed the account picker: not an error worth surfacing.
                Result.failure(SignInCancelledException(e))
            } catch (e: Exception) {
                // Broad catch: CredentialManager/Firebase Auth SDK calls above don't document a
                // stable exception surface (varies by device credential provider); log the detail
                // and convert any failure into a Result so the caller can present a generic
                // sign-in error.
                Log.w(TAG, "Sign-in failed", e)
                Result.failure(e)
            }

        override suspend fun signOut(): Result<Unit> {
            val clearResult =
                runCatchingCancellable { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            val signOutResult = runCatchingCancellable { firebaseAuth.signOut() }
            clearResult.exceptionOrNull()?.let {
                Log.w(TAG, "Failed to clear credential state during sign-out", it)
            }
            return combineSignOutResults(signOutResult)
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

            internal fun combineSignOutResults(firebaseSignOutResult: Result<Unit>): Result<Unit> =
                firebaseSignOutResult.fold(
                    onSuccess = { Result.success(Unit) },
                    onFailure = { e -> Result.failure(e) },
                )
        }
    }

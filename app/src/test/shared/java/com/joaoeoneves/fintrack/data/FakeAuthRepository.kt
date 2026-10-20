package com.joaoeoneves.fintrack.data

import android.content.Context
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [AuthRepository] implementation intended for tests. Not wired via Hilt; construct
 * and pass it directly where a fake repository is needed.
 */
class FakeAuthRepository(
    initialUser: AuthUser? = null,
    var nextSignInResult: Result<AuthUser> =
        Result.success(
            AuthUser(uid = "fake-uid", displayName = "Fake User", email = "fake@example.com", photoUrl = null),
        ),
    var nextSignOutResult: Result<Unit> = Result.success(Unit),
) : AuthRepository {
    private val userFlow = MutableStateFlow(initialUser)

    override fun observeCurrentUser(): Flow<AuthUser?> = userFlow

    override suspend fun signIn(context: Context): Result<AuthUser> {
        nextSignInResult.onSuccess { userFlow.value = it }
        return nextSignInResult
    }

    override suspend fun signOut(): Result<Unit> {
        nextSignOutResult.onSuccess { userFlow.value = null }
        return nextSignOutResult
    }
}

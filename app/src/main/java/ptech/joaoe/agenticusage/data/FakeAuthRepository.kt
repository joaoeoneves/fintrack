package ptech.joaoe.agenticusage.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ptech.joaoe.agenticusage.domain.model.AuthUser
import ptech.joaoe.agenticusage.domain.repository.AuthRepository

/**
 * In-memory [AuthRepository] implementation intended for tests. Not wired via Hilt; construct
 * and pass it directly where a fake repository is needed.
 */
class FakeAuthRepository(
    initialUser: AuthUser? = null,
    var nextSignInResult: Result<AuthUser> = Result.success(
        AuthUser(uid = "fake-uid", displayName = "Fake User", email = "fake@example.com", photoUrl = null)
    )
) : AuthRepository {

    private val userFlow = MutableStateFlow(initialUser)

    override fun observeCurrentUser(): Flow<AuthUser?> = userFlow

    override suspend fun signIn(context: Context): Result<AuthUser> {
        nextSignInResult.onSuccess { userFlow.value = it }
        return nextSignInResult
    }

    override suspend fun signOut(): Result<Unit> {
        userFlow.value = null
        return Result.success(Unit)
    }
}

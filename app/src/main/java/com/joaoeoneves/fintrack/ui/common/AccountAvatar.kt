package com.joaoeoneves.fintrack.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
private val AvatarSize = 32.dp
private val TouchTarget = 48.dp
private val PersonIconSize = 20.dp

/** First letter (uppercased) of the trimmed display name, else of the email; null when both are blank. */
fun avatarInitial(
    displayName: String?,
    email: String?,
): String? =
    listOf(displayName, email)
        .firstNotNullOfOrNull { it?.trim()?.firstOrNull() }
        ?.toString()
        ?.uppercase(Locale.ROOT)

@HiltViewModel
class AccountAvatarViewModel
    @Inject
    constructor(
        sessionRepository: SessionRepository,
    ) : ViewModel() {
        /** The signed-in user's initial; null for guests or when no name/email is known. */
        val initial: StateFlow<String?> =
            sessionRepository.session
                .map { session ->
                    (session as? Session.SignedIn)?.user?.let { avatarInitial(it.displayName, it.email) }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), null)
    }

/** The account avatar in a top bar; opens Account and settings. */
@Composable
fun AccountAvatar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountAvatarViewModel = hiltViewModel(),
) {
    val initial by viewModel.initial.collectAsStateWithLifecycle()
    AvatarButton(
        initial = initial,
        contentDescription = stringResource(R.string.cd_account_avatar),
        onClick = onClick,
        modifier = modifier,
    )
}

/** Stateless avatar: a 32dp circle (initial, or a person icon when [initial] is null) in a 48dp target. */
@Composable
fun AvatarButton(
    initial: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .testTag(TestTags.ACCOUNT_AVATAR)
                .size(TouchTarget)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .semantics {
                    this.contentDescription = contentDescription
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(AvatarSize).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (initial != null) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(PersonIconSize),
                )
            }
        }
    }
}

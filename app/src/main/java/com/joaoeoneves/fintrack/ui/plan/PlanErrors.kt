package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.asString
import kotlinx.coroutines.flow.Flow

/** Shows the messages of [events] in [snackbarHostState]. */
@Composable
fun CollectPlanErrors(
    snackbarHostState: SnackbarHostState,
    events: List<Flow<UiText>>,
) {
    val resources = LocalResources.current
    events.forEach { flow ->
        LaunchedEffect(flow) { flow.collect { snackbarHostState.showSnackbar(it.asString(resources)) } }
    }
}

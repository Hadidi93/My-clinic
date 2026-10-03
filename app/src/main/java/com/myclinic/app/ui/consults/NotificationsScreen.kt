package com.myclinic.app.ui.consults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.myclinic.app.R
import com.myclinic.app.data.notifications.NotificationRepository
import com.myclinic.app.ui.components.TouchTarget
import com.myclinic.app.ui.patients.formatDateTime
import com.myclinic.domain.consult.AppNotification
import com.myclinic.domain.consult.NotificationKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository,
) : ViewModel() {
    val notifications = repository.notifications

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun open(n: AppNotification) {
        viewModelScope.launch { repository.markRead(listOf(n.id)) }
    }

    fun markAllRead() {
        viewModelScope.launch { repository.markAllRead() }
    }
}

/** The bell list. Texts are generic; tapping one opens the consult, referral or request. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpen: (AppNotification) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val items by viewModel.notifications.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (items.any { !it.isRead }) {
                        TextButton(onClick = viewModel::markAllRead) { Text(stringResource(R.string.mark_all_read)) }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (items.isEmpty()) {
                item {
                    Text(stringResource(R.string.notifications_none), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
            items(items, key = { it.id }) { n ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = TouchTarget)
                        .clickable { viewModel.open(n); onOpen(n) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        when {
                            NotificationKind.isConsult(n.kind) -> Icons.Filled.Forum
                            NotificationKind.isReferral(n.kind) -> Icons.Filled.SwapHoriz
                            else -> Icons.Filled.Biotech
                        },
                        contentDescription = null,
                        tint = if (n.isRead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(notificationText(n.kind), style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (n.isRead) FontWeight.Normal else FontWeight.Bold)
                        formatDateTime(n.createdAt)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun notificationText(kind: String): String = stringResource(
    when (kind) {
        NotificationKind.CONSULT_REQUEST -> R.string.push_consult_request
        NotificationKind.CONSULT_MESSAGE -> R.string.push_consult_message
        NotificationKind.REFERRAL_REQUEST -> R.string.push_referral_request
        NotificationKind.REFERRAL_RESPONSE -> R.string.push_referral_response
        else -> R.string.push_lab_request
    },
)

package nl.planernl.unraider.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.planernl.unraider.R
import nl.planernl.unraider.data.NotificationItem
import nl.planernl.unraider.data.asString
import nl.planernl.unraider.ui.AutoRefresh
import nl.planernl.unraider.ui.Chassis
import nl.planernl.unraider.ui.ChassisRow
import nl.planernl.unraider.ui.ErrorState
import nl.planernl.unraider.ui.HeaderIconButton
import nl.planernl.unraider.ui.InlineError
import nl.planernl.unraider.ui.Led
import nl.planernl.unraider.ui.LoadingState
import nl.planernl.unraider.ui.MainViewModel
import nl.planernl.unraider.ui.RowDivider
import nl.planernl.unraider.ui.ScreenHeader
import nl.planernl.unraider.ui.ScreenList
import nl.planernl.unraider.ui.SmallSpinner
import nl.planernl.unraider.ui.theme.Rack

/** De meldingen uit het belletje van de webGUI. */
@Composable
fun NotificationsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.notifications.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    AutoRefresh(60_000) { vm.refreshNotifications() }

    val error = state.error?.asString()
    val list = state.data
    when {
        list == null && error != null -> ErrorState(error) { vm.refreshNotifications() }
        list == null -> LoadingState()
        else -> ScreenList(loading = state.loading, onRefresh = { vm.refreshNotifications() }) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.notifications_title),
                    subtitle = if (list.isEmpty()) stringResource(R.string.notifications_none)
                    else pluralStringResource(R.plurals.notifications_unread, list.size, list.size),
                ) {
                    if (list.isNotEmpty()) {
                        if ("notifications" in busy) SmallSpinner()
                        else TextButton(onClick = { vm.archiveAllNotifications() }) {
                            Text(stringResource(R.string.notifications_mark_all), color = Rack.accent)
                        }
                    }
                    HeaderIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick = onBack)
                }
            }
            error?.let { item { InlineError(stringResource(R.string.refresh_failed, it)) } }
            if (list.isNotEmpty()) {
                item {
                    Chassis {
                        list.forEachIndexed { i, n ->
                            if (i > 0) RowDivider()
                            NotificationRow(n, busy = n.id in busy) { vm.archiveNotification(n) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationItem, busy: Boolean, onRead: () -> Unit) {
    val color = when (n.importance) {
        "ALERT" -> Rack.critical
        "WARNING" -> Rack.warning
        else -> Rack.textSecondary
    }
    ChassisRow(verticalPadding = 12.dp) {
        Box(Modifier.padding(top = 2.dp).align(Alignment.Top)) { Led(color) }
        Column(Modifier.weight(1f)) {
            Text(n.subject.ifBlank { n.title }, style = MaterialTheme.typography.titleMedium)
            if (n.description.isNotBlank()) {
                Text(n.description, style = MaterialTheme.typography.bodyMedium, color = Rack.textSecondary)
            }
            Text(
                listOfNotNull(n.title.takeIf { n.subject.isNotBlank() && it != n.subject }, n.formattedTimestamp ?: n.timestamp)
                    .joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = Rack.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (busy) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { SmallSpinner() }
        } else {
            IconButton(onClick = onRead, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.notifications_mark_read), tint = Rack.textSecondary)
            }
        }
    }
}

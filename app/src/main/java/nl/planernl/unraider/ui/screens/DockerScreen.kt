package nl.planernl.unraider.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import nl.planernl.unraider.R
import nl.planernl.unraider.data.asString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.planernl.unraider.data.Container
import nl.planernl.unraider.data.ContainerAction
import nl.planernl.unraider.ui.AutoRefresh
import nl.planernl.unraider.ui.ConfirmDialog
import nl.planernl.unraider.ui.ErrorState
import nl.planernl.unraider.ui.InlineError
import nl.planernl.unraider.ui.LoadingState
import nl.planernl.unraider.ui.MainViewModel
import nl.planernl.unraider.ui.Pill
import nl.planernl.unraider.ui.StatusDot
import nl.planernl.unraider.ui.theme.StatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DockerScreen(vm: MainViewModel) {
    val state by vm.containers.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    AutoRefresh(20_000) { vm.refreshContainers() }

    var pulling by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) { if (!state.loading) pulling = false }

    var onlyRunning by rememberSaveable { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf<Container?>(null) }

    val errorText = state.error?.asString()
    val list = state.data
    when {
        list == null && errorText != null -> ErrorState(errorText) { vm.refreshContainers() }
        list == null -> LoadingState()
        else -> PullToRefreshBox(
            isRefreshing = pulling,
            onRefresh = { pulling = true; vm.refreshContainers() },
            modifier = Modifier.fillMaxSize(),
        ) {
            val shown = if (onlyRunning) list.filter { it.running } else list
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                errorText?.let { err -> item { InlineError(stringResource(R.string.refresh_failed, err)) } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = !onlyRunning, onClick = { onlyRunning = false }, label = { Text(stringResource(R.string.filter_all, list.size)) })
                        FilterChip(
                            selected = onlyRunning,
                            onClick = { onlyRunning = true },
                            label = { Text(stringResource(R.string.filter_running, list.count { it.running })) },
                        )
                    }
                }
                items(shown, key = { it.id }) { c ->
                    ContainerRow(
                        c = c,
                        busy = c.id in busy,
                        onAction = { action ->
                            if (action == ContainerAction.STOP) confirmStop = c else vm.containerAction(c, action)
                        },
                    )
                }
            }
        }
    }

    confirmStop?.let { c ->
        ConfirmDialog(
            title = stringResource(R.string.container_stop_title, c.name),
            text = stringResource(R.string.container_stop_text),
            confirmLabel = stringResource(R.string.action_stop),
            destructive = true,
            onConfirm = { vm.containerAction(c, ContainerAction.STOP) },
            onDismiss = { confirmStop = null },
        )
    }
}

@Composable
private fun ContainerRow(c: Container, busy: Boolean, onAction: (ContainerAction) -> Unit) {
    val uri = LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    val color = when {
        c.running -> StatusColors.ok
        c.paused -> StatusColors.warning
        else -> StatusColors.sleeping
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color, 10)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (c.updateAvailable == true) {
                        Spacer(Modifier.width(6.dp))
                        Pill(stringResource(R.string.update_badge), StatusColors.warning)
                    }
                }
                Text(
                    c.status.ifBlank { c.state.lowercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    c.image,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (busy) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else if (c.running || c.paused) {
                IconButton(onClick = { onAction(ContainerAction.STOP) }) { Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.action_stop)) }
            } else {
                IconButton(onClick = { onAction(ContainerAction.START) }) { Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.action_start)) }
            }

            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (c.running) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_restart)) }, onClick = { menu = false; onAction(ContainerAction.RESTART) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_pause)) }, onClick = { menu = false; onAction(ContainerAction.PAUSE) })
                    }
                    if (c.paused) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_resume)) }, onClick = { menu = false; onAction(ContainerAction.UNPAUSE) })
                    }
                    c.webUiUrl?.takeIf { c.running }?.let { url ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.open_webui)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                            onClick = { menu = false; runCatching { uri.openUri(url) } },
                        )
                    }
                    c.autoStart?.let {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (it) R.string.autostart_on else R.string.autostart_off)) },
                            enabled = false,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}

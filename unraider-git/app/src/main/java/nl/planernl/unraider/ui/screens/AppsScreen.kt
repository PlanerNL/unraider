package nl.planernl.unraider.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.planernl.unraider.R
import nl.planernl.unraider.data.Container
import nl.planernl.unraider.data.ContainerAction
import nl.planernl.unraider.data.Load
import nl.planernl.unraider.data.PluginInfo
import nl.planernl.unraider.data.asString
import nl.planernl.unraider.ui.AutoRefresh
import nl.planernl.unraider.ui.Chassis
import nl.planernl.unraider.ui.ChassisRow
import nl.planernl.unraider.ui.ConfirmDialog
import nl.planernl.unraider.ui.ErrorState
import nl.planernl.unraider.ui.IconTile
import nl.planernl.unraider.ui.InlineError
import nl.planernl.unraider.ui.LoadingState
import nl.planernl.unraider.ui.MainViewModel
import nl.planernl.unraider.ui.RackSwitch
import nl.planernl.unraider.ui.RowDivider
import nl.planernl.unraider.ui.ScreenHeader
import nl.planernl.unraider.ui.ScreenList
import nl.planernl.unraider.ui.SegmentedControl
import nl.planernl.unraider.ui.SmallSpinner
import nl.planernl.unraider.ui.theme.Rack

/** Docker-containers en plugins onder één tabblad. */
@Composable
fun AppsScreen(vm: MainViewModel, baseUrl: String, onOpenWebGui: (path: String) -> Unit) {
    val containers by vm.containers.collectAsStateWithLifecycle()
    val plugins by vm.plugins.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }

    AutoRefresh(20_000) { vm.refreshContainers() }
    AutoRefresh(300_000) { vm.refreshPlugins() }

    var confirmStop by remember { mutableStateOf<Container?>(null) }

    val current: Load<*> = if (tab == 0) containers else plugins
    ScreenList(
        loading = current.loading,
        onRefresh = { if (tab == 0) vm.refreshContainers() else vm.refreshPlugins() },
    ) {
        item {
            val c = containers.data
            val subtitle = if (tab == 0) {
                c?.let {
                    val running = it.count { x -> x.running }
                    val updates = it.count { x -> x.updateAvailable == true }
                    stringResource(R.string.apps_docker_summary, running, it.size) +
                        if (updates > 0) ", " + pluralStringResource(R.plurals.updates_available, updates, updates) else ""
                }
            } else {
                plugins.data?.let { pluralStringResource(R.plurals.plugins_installed, it.size, it.size) }
            }
            ScreenHeader(title = stringResource(R.string.tab_apps), subtitle = subtitle)
        }
        item {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.apps_docker) to containers.data?.size?.toString(),
                    stringResource(R.string.apps_plugins) to plugins.data?.size?.toString(),
                ),
                selected = tab,
                onSelect = { tab = it },
            )
        }

        if (tab == 0) {
            val list = containers.data
            val err = containers.error
            when {
                list == null && err != null -> item { ErrorBlock(err.asString()) { vm.refreshContainers() } }
                list == null -> item { LoadingBlock() }
                else -> {
                    err?.let { e -> item { InlineError(stringResource(R.string.refresh_failed, e.asString())) } }
                    item {
                        Chassis {
                            list.forEachIndexed { i, c ->
                                if (i > 0) RowDivider()
                                ContainerRow(
                                    c = c,
                                    baseUrl = baseUrl,
                                    busy = c.id in busy,
                                    onToggle = { on -> if (on) vm.containerAction(c, ContainerAction.START) else confirmStop = c },
                                    onAction = { vm.containerAction(c, it) },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val list = plugins.data
            val err = plugins.error
            when {
                list == null && err != null -> item { ErrorBlock(err.asString()) { vm.refreshPlugins() } }
                list == null -> item { LoadingBlock() }
                list.isEmpty() -> item {
                    Text(stringResource(R.string.no_plugins), style = MaterialTheme.typography.bodyMedium, color = Rack.textSecondary)
                }
                else -> {
                    item {
                        Chassis {
                            list.forEachIndexed { i, p ->
                                if (i > 0) RowDivider()
                                PluginRow(p, baseUrl) { onOpenWebGui("/Plugins") }
                            }
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.plugins_icon_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = Rack.textSecondary,
                        )
                    }
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
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(320.dp)) { ErrorState(message, onRetry) }
}

@Composable
private fun LoadingBlock() {
    Box(Modifier.fillMaxWidth().height(240.dp)) { LoadingState() }
}

@Composable
private fun ContainerRow(
    c: Container,
    baseUrl: String,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onAction: (ContainerAction) -> Unit,
) {
    val uri = LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    val status = when {
        c.running -> Rack.ok
        c.paused -> Rack.warning
        else -> Rack.ledOff
    }
    ChassisRow(verticalPadding = 10.dp) {
        // Icoon is soms een pad op de server zelf; dan via de WebGUI-sessie ophalen
        val icon = c.iconUrl?.let { if (it.startsWith("/")) baseUrl + it else it }
        IconTile(icon, Icons.Outlined.ViewInAr, statusColor = status, dimmed = !c.running && !c.paused)
        Column(Modifier.weight(1f)) {
            Text(
                c.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (c.running) Rack.text else Rack.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (c.updateAvailable == true) stringResource(R.string.update_available) else c.status.ifBlank { c.state.lowercase() },
                style = MaterialTheme.typography.bodySmall,
                color = if (c.updateAvailable == true) Rack.warning else Rack.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) {
            Box(Modifier.size(52.dp, 32.dp), contentAlignment = Alignment.Center) { SmallSpinner() }
        } else {
            RackSwitch(
                checked = c.running || c.paused,
                onCheckedChange = onToggle,
                label = stringResource(R.string.a11y_toggle, c.name),
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more), tint = Rack.textSecondary)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Rack.chassis) {
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

@Composable
private fun PluginRow(p: PluginInfo, baseUrl: String, onOpen: () -> Unit) {
    ChassisRow(onClick = onOpen, verticalPadding = 10.dp) {
        IconTile(pluginIconUrl(baseUrl, p), Icons.Outlined.Extension)
        Column(Modifier.weight(1f)) {
            Text(p.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.file, style = MaterialTheme.typography.bodySmall, color = Rack.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = stringResource(R.string.a11y_open_in_webgui, p.displayName),
            tint = Rack.textSecondary,
        )
    }
}

/**
 * De API geeft geen icoon voor plugins. Veel plugins leveren er wel een mee op deze plek;
 * de app haalt hem op met de inlogsessie van het WebGUI-tabblad (zie [nl.planernl.unraider.data.IconLoader]).
 */
fun pluginIconUrl(baseUrl: String, p: PluginInfo): String =
    "$baseUrl/plugins/${p.baseName}/images/${p.baseName}.png"

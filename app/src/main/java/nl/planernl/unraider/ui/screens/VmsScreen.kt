package nl.planernl.unraider.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.planernl.unraider.R
import nl.planernl.unraider.data.Vm
import nl.planernl.unraider.data.VmAction
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
import nl.planernl.unraider.ui.SmallSpinner
import nl.planernl.unraider.ui.theme.Rack

@Composable
fun VmsScreen(vm: MainViewModel) {
    val state by vm.vms.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    AutoRefresh(20_000) { vm.refreshVms() }

    var confirm by remember { mutableStateOf<Pair<Vm, VmAction>?>(null) }

    val error = state.error?.asString()
    val list = state.data
    when {
        list == null && error != null -> ErrorState(error) { vm.refreshVms() }
        list == null -> LoadingState()
        else -> ScreenList(loading = state.loading, onRefresh = { vm.refreshVms() }) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.tab_vms),
                    subtitle = stringResource(R.string.vms_summary, list.count { it.running }, list.size),
                )
            }
            error?.let { item { InlineError(stringResource(R.string.refresh_failed, it)) } }
            if (list.isEmpty()) {
                item { Text(stringResource(R.string.no_vms), style = MaterialTheme.typography.bodyMedium, color = Rack.textSecondary) }
            } else {
                item {
                    Chassis {
                        list.forEachIndexed { i, machine ->
                            if (i > 0) RowDivider()
                            VmRow(
                                vm = machine,
                                busy = machine.id in busy,
                                onToggle = { on ->
                                    when {
                                        on && machine.paused -> vm.vmAction(machine, VmAction.RESUME)
                                        on -> vm.vmAction(machine, VmAction.START)
                                        else -> confirm = machine to VmAction.STOP
                                    }
                                },
                                onAction = { a -> confirm = machine to a },
                            )
                        }
                    }
                }
            }
        }
    }

    confirm?.let { (machine, action) ->
        val (titleRes, textRes) = when (action) {
            VmAction.STOP -> R.string.vm_stop_title to R.string.vm_stop_text
            VmAction.FORCE_STOP -> R.string.vm_force_title to R.string.vm_force_text
            VmAction.REBOOT -> R.string.vm_reboot_title to R.string.vm_reboot_text
            else -> R.string.vm_pause_title to R.string.vm_pause_text
        }
        ConfirmDialog(
            title = stringResource(titleRes, machine.name),
            text = stringResource(textRes),
            confirmLabel = stringResource(R.string.action_continue),
            destructive = action == VmAction.FORCE_STOP,
            onConfirm = { vm.vmAction(machine, action) },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun VmRow(vm: Vm, busy: Boolean, onToggle: (Boolean) -> Unit, onAction: (VmAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val status = when {
        vm.running -> Rack.ok
        vm.paused -> Rack.warning
        vm.state == "CRASHED" -> Rack.critical
        else -> Rack.ledOff
    }
    ChassisRow(verticalPadding = 10.dp) {
        IconTile(null, Icons.Outlined.Computer, statusColor = status, dimmed = !vm.running && !vm.paused)
        Column(Modifier.weight(1f)) {
            Text(vm.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (vm.running) Rack.text else Rack.textSecondary)
            Text(vm.stateText.asString(), style = MaterialTheme.typography.bodySmall, color = Rack.textSecondary)
        }
        if (busy) {
            Box(Modifier.size(52.dp, 32.dp), contentAlignment = Alignment.Center) { SmallSpinner() }
        } else {
            RackSwitch(checked = vm.running, onCheckedChange = onToggle, label = stringResource(R.string.a11y_toggle, vm.name))
        }
        if (vm.running || vm.paused) {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more), tint = Rack.textSecondary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Rack.chassis) {
                    if (vm.running) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_restart)) }, onClick = { menu = false; onAction(VmAction.REBOOT) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_pause)) }, onClick = { menu = false; onAction(VmAction.PAUSE) })
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_force_off), color = Rack.critical) },
                        onClick = { menu = false; onAction(VmAction.FORCE_STOP) },
                    )
                }
            }
        }
    }
}

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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import nl.planernl.unraider.R
import nl.planernl.unraider.data.asString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.planernl.unraider.data.Vm
import nl.planernl.unraider.data.VmAction
import nl.planernl.unraider.ui.AutoRefresh
import nl.planernl.unraider.ui.ConfirmDialog
import nl.planernl.unraider.ui.ErrorState
import nl.planernl.unraider.ui.InlineError
import nl.planernl.unraider.ui.LoadingState
import nl.planernl.unraider.ui.MainViewModel
import nl.planernl.unraider.ui.StatusDot
import nl.planernl.unraider.ui.theme.StatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmsScreen(vm: MainViewModel) {
    val state by vm.vms.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    AutoRefresh(20_000) { vm.refreshVms() }

    var pulling by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) { if (!state.loading) pulling = false }

    var confirm by remember { mutableStateOf<Pair<Vm, VmAction>?>(null) }

    val errorText = state.error?.asString()
    val list = state.data
    when {
        list == null && errorText != null -> ErrorState(errorText) { vm.refreshVms() }
        list == null -> LoadingState()
        else -> PullToRefreshBox(
            isRefreshing = pulling,
            onRefresh = { pulling = true; vm.refreshVms() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                errorText?.let { err -> item { InlineError(stringResource(R.string.refresh_failed, err)) } }
                if (list.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_vms),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                items(list, key = { it.id }) { machine ->
                    VmRow(machine, machine.id in busy) { action ->
                        when (action) {
                            VmAction.START, VmAction.RESUME -> vm.vmAction(machine, action)
                            else -> confirm = machine to action
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
private fun VmRow(vm: Vm, busy: Boolean, onAction: (VmAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val color = when {
        vm.running -> StatusColors.ok
        vm.paused -> StatusColors.warning
        vm.state == "CRASHED" -> StatusColors.critical
        else -> StatusColors.sleeping
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color, 10)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(vm.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(vm.stateText.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                busy -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                vm.running -> IconButton(onClick = { onAction(VmAction.STOP) }) {
                    Icon(Icons.Outlined.PowerSettingsNew, contentDescription = stringResource(R.string.action_shutdown))
                }
                vm.paused -> IconButton(onClick = { onAction(VmAction.RESUME) }) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.action_resume))
                }
                else -> IconButton(onClick = { onAction(VmAction.START) }) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.action_start))
                }
            }
            if (vm.running || vm.paused) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (vm.running) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_restart)) }, onClick = { menu = false; onAction(VmAction.REBOOT) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_pause)) }, onClick = { menu = false; onAction(VmAction.PAUSE) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_force_off)) }, onClick = { menu = false; onAction(VmAction.FORCE_STOP) })
                    }
                }
            }
        }
    }
}

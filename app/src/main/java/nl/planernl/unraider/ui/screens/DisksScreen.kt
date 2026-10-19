package nl.planernl.unraider.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.planernl.unraider.R
import nl.planernl.unraider.data.ArrayDiskInfo
import nl.planernl.unraider.data.DiskOverview
import nl.planernl.unraider.data.Health
import nl.planernl.unraider.data.Load
import nl.planernl.unraider.data.ParityAction
import nl.planernl.unraider.data.PhysicalDiskInfo
import nl.planernl.unraider.data.arrayStateText
import nl.planernl.unraider.data.asString
import nl.planernl.unraider.data.formatBytes
import nl.planernl.unraider.data.formatCount
import nl.planernl.unraider.data.formatKb
import nl.planernl.unraider.data.statusText
import nl.planernl.unraider.ui.AutoRefresh
import nl.planernl.unraider.ui.ConfirmDialog
import nl.planernl.unraider.ui.ErrorState
import nl.planernl.unraider.ui.InlineError
import nl.planernl.unraider.ui.LoadingState
import nl.planernl.unraider.ui.MainViewModel
import nl.planernl.unraider.ui.Pill
import nl.planernl.unraider.ui.SectionCard
import nl.planernl.unraider.ui.SectionHeader
import nl.planernl.unraider.ui.StatusDot
import nl.planernl.unraider.ui.UsageBar
import nl.planernl.unraider.ui.formatTime
import nl.planernl.unraider.ui.theme.StatusColors

private const val REFRESH_MS = 30_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisksScreen(vm: MainViewModel) {
    val state by vm.disks.collectAsStateWithLifecycle()
    val smart by vm.smart.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    AutoRefresh(REFRESH_MS) { vm.refreshDisks() }

    var pulling by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) { if (!state.loading) pulling = false }

    var confirmArray by remember { mutableStateOf<Boolean?>(null) } // true = starten, false = stoppen
    var showParityDialog by remember { mutableStateOf(false) }
    var confirmCancelParity by remember { mutableStateOf(false) }

    val titleParity = stringResource(R.string.section_parity)
    val titleArray = stringResource(R.string.section_array)
    val titleCache = stringResource(R.string.section_cache)
    val errorText = state.error?.asString()

    val data = state.data
    when {
        data == null && errorText != null -> ErrorState(errorText) { vm.refreshDisks() }
        data == null -> LoadingState()
        else -> PullToRefreshBox(
            isRefreshing = pulling,
            onRefresh = { pulling = true; vm.refreshDisks() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                errorText?.let { err -> item { InlineError(stringResource(R.string.refresh_failed, err)) } }

                item {
                    ArraySummaryCard(
                        data = data,
                        updatedAt = state.updatedAt,
                        arrayBusy = "array" in busy,
                        parityBusy = "parity" in busy,
                        onArrayToggle = { start -> confirmArray = start },
                        onParityStart = { showParityDialog = true },
                        onParityPause = { vm.parityAction(ParityAction.PAUSE) },
                        onParityResume = { vm.parityAction(ParityAction.RESUME) },
                        onParityCancel = { confirmCancelParity = true },
                    )
                }

                fun section(key: String, title: String, disks: List<ArrayDiskInfo>) {
                    if (disks.isEmpty()) return
                    item(key = "h_$key") {
                        SectionHeader(title, pluralStringResource(R.plurals.disks_count, disks.size, disks.size))
                    }
                    items(disks, key = { "${key}_${it.id}_${it.name}" }) { disk ->
                        DiskCard(disk, smart.data?.let { list -> matchPhysical(list, disk) })
                    }
                }
                section("parity", titleParity, data.parities)
                section("array", titleArray, data.data)
                section("cache", titleCache, data.caches)

                item {
                    Spacer(Modifier.height(8.dp))
                    SmartCard(smart, data, onLoad = vm::loadSmart)
                }
            }
        }
    }

    confirmArray?.let { start ->
        ConfirmDialog(
            title = stringResource(if (start) R.string.array_start_title else R.string.array_stop_title),
            text = stringResource(if (start) R.string.array_start_text else R.string.array_stop_text),
            confirmLabel = stringResource(if (start) R.string.action_start else R.string.action_stop),
            destructive = !start,
            onConfirm = { vm.setArrayState(start) },
            onDismiss = { confirmArray = null },
        )
    }

    if (showParityDialog) {
        AlertDialog(
            onDismissRequest = { showParityDialog = false },
            title = { Text(stringResource(R.string.parity_dialog_title)) },
            text = { Text(stringResource(R.string.parity_dialog_text)) },
            confirmButton = {
                TextButton(onClick = { vm.parityAction(ParityAction.START_CORRECT); showParityDialog = false }) {
                    Text(stringResource(R.string.parity_with_corrections))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.parityAction(ParityAction.START_CHECK); showParityDialog = false }) {
                    Text(stringResource(R.string.parity_check_only))
                }
            },
        )
    }

    if (confirmCancelParity) {
        ConfirmDialog(
            title = stringResource(R.string.parity_cancel_title),
            text = stringResource(R.string.parity_cancel_text),
            confirmLabel = stringResource(R.string.parity_cancel_confirm),
            destructive = true,
            onConfirm = { vm.parityAction(ParityAction.CANCEL) },
            onDismiss = { confirmCancelParity = false },
        )
    }
}

private fun matchPhysical(list: List<PhysicalDiskInfo>, disk: ArrayDiskInfo): PhysicalDiskInfo? {
    val dev = disk.device ?: return null
    return list.firstOrNull { it.device.substringAfterLast('/') == dev }
}

@Composable
private fun ArraySummaryCard(
    data: DiskOverview,
    updatedAt: Long?,
    arrayBusy: Boolean,
    parityBusy: Boolean,
    onArrayToggle: (start: Boolean) -> Unit,
    onParityStart: () -> Unit,
    onParityPause: () -> Unit,
    onParityResume: () -> Unit,
    onParityCancel: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.section_array),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            val stateColor = when (data.arrayState) {
                "STARTED" -> StatusColors.ok
                "STOPPED" -> StatusColors.sleeping
                else -> StatusColors.critical
            }
            Pill(arrayStateText(data.arrayState).asString(), stateColor)
        }

        val used = data.capacityUsedKb
        val total = data.capacityTotalKb
        if (used != null && total != null && total > 0) {
            Spacer(Modifier.height(14.dp))
            val fraction = used.toFloat() / total
            UsageBar(fraction)
            Spacer(Modifier.height(6.dp))
            Row {
                Text(
                    stringResource(R.string.used_of, formatKb(used), formatKb(total)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val n = data.all.size
            Pill(pluralStringResource(R.plurals.disks_count, n, n), MaterialTheme.colorScheme.onSurfaceVariant)
            if (data.sleepingCount > 0) {
                Pill(pluralStringResource(R.plurals.sleeping_count, data.sleepingCount, data.sleepingCount), StatusColors.sleeping)
            }
            if (data.warnings > 0) {
                Pill(pluralStringResource(R.plurals.warnings_count, data.warnings, data.warnings), StatusColors.warning)
            } else {
                Pill(stringResource(R.string.all_ok), StatusColors.ok)
            }
        }

        // Parity-check
        data.parity?.let { p ->
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.parity_check),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            val errorText = p.errors?.let {
                if (it == 0) stringResource(R.string.no_errors) else pluralStringResource(R.plurals.errors_count, it, it)
            }
            Text(
                listOfNotNull(p.text.asString(), errorText, p.speed?.takeIf { p.isActive }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = if ((p.errors ?: 0) > 0) StatusColors.warning else MaterialTheme.colorScheme.onSurface,
            )
            if (p.isActive && p.progress != null) {
                Spacer(Modifier.height(6.dp))
                UsageBar(p.progress / 100f)
            }
            p.date?.takeIf { !p.isActive }?.let {
                Text(
                    stringResource(R.string.parity_last, it.take(10)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Acties
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val started = data.arrayState == "STARTED"
            OutlinedButton(onClick = { onArrayToggle(!started) }, enabled = !arrayBusy) {
                Text(stringResource(if (started) R.string.array_stop_button else R.string.array_start_button))
            }
            val p = data.parity
            if (started && p != null) {
                when {
                    p.status == "PAUSED" || p.paused == true -> {
                        TextButton(onClick = onParityResume, enabled = !parityBusy) { Text(stringResource(R.string.action_resume)) }
                        TextButton(onClick = onParityCancel, enabled = !parityBusy) { Text(stringResource(R.string.action_stop)) }
                    }
                    p.isActive -> {
                        TextButton(onClick = onParityPause, enabled = !parityBusy) { Text(stringResource(R.string.action_pause)) }
                        TextButton(onClick = onParityCancel, enabled = !parityBusy) { Text(stringResource(R.string.action_stop)) }
                    }
                    data.parities.isNotEmpty() ->
                        TextButton(onClick = onParityStart, enabled = !parityBusy) { Text(stringResource(R.string.parity_check)) }
                }
            }
            if (arrayBusy || parityBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.updated_every, formatTime(updatedAt), (REFRESH_MS / 1000).toInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiskCard(disk: ArrayDiskInfo, physical: PhysicalDiskInfo?) {
    var expanded by rememberSaveable(disk.id, disk.name) { mutableStateOf(false) }
    val health = disk.health
    val healthColor = StatusColors.of(health)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(healthColor, 12)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(disk.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(
                            disk.device,
                            formatKb(disk.sizeKb).takeIf { disk.sizeKb != null },
                            disk.fsType,
                            if (disk.isSsd) "SSD" else if (disk.rotational == true) "HDD" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TemperaturePill(disk)
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.less else R.string.more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            disk.usedFraction?.let { fraction ->
                Spacer(Modifier.height(12.dp))
                UsageBar(fraction)
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        stringResource(R.string.size_used, formatKb(disk.fsUsedKb)),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.size_free, formatKb(disk.fsFreeKb)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val problems = disk.problems
            if (problems.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                problems.forEach {
                    Text(
                        "• ${it.asString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (health == Health.CRITICAL) StatusColors.critical else StatusColors.warning,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    DetailRow(stringResource(R.string.label_status), statusText(disk.status).asString())
                    DetailRow(stringResource(R.string.label_identification), disk.identification)
                    val isDefault = disk.warningTemp == null || disk.warningTemp == 0
                    DetailRow(
                        stringResource(R.string.label_temp_limits),
                        stringResource(
                            if (isDefault) R.string.temp_limits_default else R.string.temp_limits,
                            disk.effectiveWarningTemp,
                            disk.effectiveCriticalTemp,
                        ),
                    )
                    disk.transport?.let { DetailRow(stringResource(R.string.label_connection), it.uppercase()) }
                    disk.isSpinning?.let {
                        DetailRow(stringResource(R.string.label_spinning), stringResource(if (it) R.string.yes else R.string.spun_down))
                    }
                    DetailRow(stringResource(R.string.label_reads), formatCount(disk.numReads))
                    DetailRow(stringResource(R.string.label_writes), formatCount(disk.numWrites))
                    DetailRow(
                        stringResource(R.string.label_io_errors),
                        (disk.numErrors ?: 0).toString(),
                        if ((disk.numErrors ?: 0) > 0) StatusColors.warning else null,
                    )
                    disk.idx?.let { DetailRow(stringResource(R.string.label_slot), it.toString()) }

                    if (physical != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("SMART", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DetailRow(
                            stringResource(R.string.label_smart_status),
                            smartText(physical.smartStatus),
                            if (physical.smartStatus == "OK") StatusColors.ok else null,
                        )
                        DetailRow(stringResource(R.string.label_model), listOfNotNull(physical.vendor, physical.name).joinToString(" "))
                        physical.serial?.let { DetailRow(stringResource(R.string.label_serial), it) }
                        physical.firmware?.let { DetailRow(stringResource(R.string.label_firmware), it) }
                        physical.interfaceType?.let { DetailRow(stringResource(R.string.label_interface), it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemperaturePill(disk: ArrayDiskInfo) {
    val temp = disk.temp
    when {
        temp != null -> {
            val color = when {
                temp >= disk.effectiveCriticalTemp -> StatusColors.critical
                temp >= disk.effectiveWarningTemp -> StatusColors.warning
                else -> StatusColors.ok
            }
            Pill("$temp °C", color)
        }
        disk.sleeping -> Row(verticalAlignment = Alignment.CenterVertically) {
            val label = stringResource(R.string.sleeping)
            Icon(Icons.Outlined.Bedtime, contentDescription = label, tint = StatusColors.sleeping, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = StatusColors.sleeping)
        }
        else -> Pill("– °C", StatusColors.sleeping)
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color? = null) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(150.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun smartText(s: String?): String = when (s) {
    "OK" -> "OK"
    "UNKNOWN", null -> stringResource(R.string.unknown)
    else -> s
}

@Composable
private fun SmartCard(smart: Load<List<PhysicalDiskInfo>>, overview: DiskOverview, onLoad: () -> Unit) {
    SectionCard {
        Text(stringResource(R.string.smart_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.smart_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onLoad, enabled = !smart.loading) {
                Text(stringResource(if (smart.data == null) R.string.smart_fetch else R.string.smart_refetch))
            }
            Spacer(Modifier.width(12.dp))
            when {
                smart.loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                smart.updatedAt != null -> Text(
                    stringResource(R.string.fetched_at, formatTime(smart.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        smart.error?.let {
            Spacer(Modifier.height(8.dp))
            InlineError(it.asString())
        }

        // Schijven die wel fysiek aanwezig zijn maar niet in array of pool zitten
        val list = smart.data
        if (list != null) {
            val assigned = overview.all.mapNotNull { it.device }.toSet()
            val unassigned = list.filter { it.device.substringAfterLast('/') !in assigned }
            if (unassigned.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.unassigned),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                unassigned.forEach { d ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(if (d.smartStatus == "OK") StatusColors.ok else StatusColors.sleeping, 8)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                listOfNotNull(d.vendor, d.name).joinToString(" ").ifBlank { d.device },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            d.temperature?.let { Text("${it.toInt()} °C", style = MaterialTheme.typography.bodySmall) }
                        }
                        Text(
                            listOfNotNull(
                                d.device,
                                formatBytes(d.sizeBytes),
                                d.interfaceType,
                                "SMART: ${smartText(d.smartStatus)}",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

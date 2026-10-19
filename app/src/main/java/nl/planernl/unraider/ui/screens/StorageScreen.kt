package nl.planernl.unraider.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import nl.planernl.unraider.ui.BarSegment
import nl.planernl.unraider.ui.Bar
import nl.planernl.unraider.ui.Chassis
import nl.planernl.unraider.ui.ChassisRow
import nl.planernl.unraider.ui.ConfirmDialog
import nl.planernl.unraider.ui.ErrorState
import nl.planernl.unraider.ui.HeaderIconButton
import nl.planernl.unraider.ui.InlineError
import nl.planernl.unraider.ui.Led
import nl.planernl.unraider.ui.LoadingState
import nl.planernl.unraider.ui.MainViewModel
import nl.planernl.unraider.ui.Panel
import nl.planernl.unraider.ui.RowDivider
import nl.planernl.unraider.ui.ScreenHeader
import nl.planernl.unraider.ui.ScreenList
import nl.planernl.unraider.ui.SectionLabel
import nl.planernl.unraider.ui.SegmentedBar
import nl.planernl.unraider.ui.SmallSpinner
import nl.planernl.unraider.ui.formatTime
import nl.planernl.unraider.ui.theme.Rack

@Composable
fun StorageScreen(vm: MainViewModel) {
    val state by vm.disks.collectAsStateWithLifecycle()
    val smart by vm.smart.collectAsStateWithLifecycle()
    val server by vm.server.collectAsStateWithLifecycle()

    AutoRefresh(30_000) { vm.refreshDisks() }

    var detail by remember { mutableStateOf<ArrayDiskInfo?>(null) }
    var showControls by remember { mutableStateOf(false) }

    val error = state.error?.asString()
    val data = state.data
    when {
        data == null && error != null -> ErrorState(error) { vm.refreshDisks() }
        data == null -> LoadingState()
        else -> ScreenList(loading = state.loading, onRefresh = { vm.refreshDisks() }) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.tab_storage),
                    subtitle = arrayLine(data, server.data?.name),
                    subtitleLed = if (data.arrayState == "STARTED") Rack.ok else Rack.critical,
                ) {
                    HeaderIconButton(Icons.Outlined.Tune, stringResource(R.string.array_controls)) { showControls = true }
                }
            }
            error?.let { item { InlineError(stringResource(R.string.refresh_failed, it)) } }
            item { StorageSummary(data) }

            val arrayDisks = data.parities + data.data
            if (arrayDisks.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.section_array)) }
                item { DiskGroup(arrayDisks, smart.data) { detail = it } }
            }
            if (data.caches.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.section_pool)) }
                item { DiskGroup(data.caches, smart.data) { detail = it } }
            }
            item { SmartPanel(smart, data, onLoad = vm::loadSmart) }
            item {
                Text(
                    stringResource(R.string.updated_at, formatTime(state.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Rack.textSecondary,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }

    detail?.let { d ->
        DiskDetailSheet(d, smart.data?.let { matchPhysical(it, d) }, onDismiss = { detail = null })
    }
    if (showControls && data != null) {
        ArrayControlsSheet(vm, data, onDismiss = { showControls = false })
    }
}

@Composable
private fun arrayLine(data: DiskOverview, server: String?): String {
    val state = arrayStateText(data.arrayState).asString()
    return if (server != null) stringResource(R.string.array_on_server, state, server) else state
}

// ---------------------------------------------------------------------------
// Samenvatting (ook gebruikt op het overzicht)
// ---------------------------------------------------------------------------

@Composable
fun StorageSummary(data: DiskOverview, onClick: (() -> Unit)? = null) {
    val hottest = data.all.filter { it.temp != null }.maxByOrNull { it.temp ?: 0 }
    Panel(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(stringResource(R.string.stat_used), formatKb(data.capacityUsedKb), Modifier.weight(1f))
            Stat(
                stringResource(R.string.stat_free),
                formatKb(data.capacityTotalKb?.let { t -> data.capacityUsedKb?.let { t - it } }),
                Modifier.weight(1f),
            )
            Stat(
                stringResource(R.string.stat_hottest),
                hottest?.temp?.let { "$it°" } ?: "–",
                Modifier.weight(1f),
                color = hottest?.let { tempColor(it) } ?: Rack.text,
            )
        }
        Spacer(Modifier.height(14.dp))
        val segments = data.data.map {
            BarSegment(
                weight = (it.sizeKb ?: 1L).toFloat(),
                fraction = it.usedFraction ?: 0f,
                color = Rack.fill(it.health),
            )
        }
        SegmentedBar(segments, stringResource(R.string.a11y_disk_bar))
        Spacer(Modifier.height(10.dp))
        val p = data.parity
        val line = when {
            data.warnings > 0 -> pluralStringResource(R.plurals.warnings_count, data.warnings, data.warnings)
            p != null -> stringResource(R.string.parity_line, p.text.asString()) +
                (p.errors?.takeIf { it > 0 }?.let { ", " + pluralStringResource(R.plurals.errors_count, it, it) } ?: "")
            else -> stringResource(R.string.all_ok)
        }
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = if (data.warnings > 0) Rack.warning else Rack.textSecondary,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, color: Color = Rack.text) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Rack.textSecondary)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
    }
}

fun tempColor(d: ArrayDiskInfo): Color {
    val t = d.temp ?: return Rack.textSecondary
    return when {
        t >= d.effectiveCriticalTemp -> Rack.critical
        t >= d.effectiveWarningTemp -> Rack.warning
        else -> Rack.text
    }
}

// ---------------------------------------------------------------------------
// Schijflijst
// ---------------------------------------------------------------------------

@Composable
private fun DiskGroup(disks: List<ArrayDiskInfo>, smart: List<PhysicalDiskInfo>?, onOpen: (ArrayDiskInfo) -> Unit) {
    Chassis {
        disks.forEachIndexed { i, d ->
            if (i > 0) RowDivider()
            DiskRow(d) { onOpen(d) }
        }
    }
}

@Composable
private fun DiskRow(d: ArrayDiskInfo, onClick: () -> Unit) {
    val h = d.health
    ChassisRow(onClick = onClick) {
        Led(Rack.led(h))
        Column(Modifier.weight(1f)) {
            Text(
                d.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = if (h == Health.SLEEPING) Rack.textSecondary else Rack.text,
            )
            Text(
                diskSubline(d),
                style = MaterialTheme.typography.bodySmall,
                color = when (h) {
                    Health.WARNING -> Rack.warning
                    Health.CRITICAL -> Rack.critical
                    else -> Rack.textSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        d.usedFraction?.let { Bar(it, Rack.fill(h), Modifier.width(56.dp)) }
        Text(
            d.temp?.let { "$it°" } ?: "–",
            style = MaterialTheme.typography.titleMedium,
            color = tempColor(d),
            modifier = Modifier.width(40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun diskSubline(d: ArrayDiskInfo): String {
    val parts = mutableListOf<String>()
    when (d.health) {
        Health.SLEEPING -> parts += stringResource(R.string.sleeping)
        Health.WARNING, Health.CRITICAL -> d.problems.firstOrNull()?.let { parts += it.asString() }
        else -> d.device?.let { parts += it }
    }
    d.sizeKb?.let { parts += formatKb(it) }
    d.usedFraction?.let { parts += "${(it * 100).toInt()}%" }
    return parts.joinToString(", ")
}

private fun matchPhysical(list: List<PhysicalDiskInfo>, disk: ArrayDiskInfo): PhysicalDiskInfo? {
    val dev = disk.device ?: return null
    return list.firstOrNull { it.device.substringAfterLast('/') == dev }
}

// ---------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiskDetailSheet(d: ArrayDiskInfo, physical: PhysicalDiskInfo?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Rack.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.displayName, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        listOfNotNull(
                            d.device,
                            d.sizeKb?.let { formatKb(it) },
                            if (d.isSsd) "SSD" else if (d.rotational == true) "HDD" else null,
                            d.fsType,
                        ).joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Rack.textSecondary,
                    )
                }
                Text(
                    d.temp?.let { "$it°" } ?: stringResource(R.string.sleeping),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 40.sp),
                    color = tempColor(d),
                )
            }

            d.problems.takeIf { it.isNotEmpty() }?.let { problems ->
                Panel {
                    problems.forEach {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Led(Rack.led(d.health))
                            Spacer(Modifier.width(10.dp))
                            Text(it.asString(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            d.usedFraction?.let { f ->
                Panel {
                    Row {
                        Text(stringResource(R.string.size_used, formatKb(d.fsUsedKb)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.size_free, formatKb(d.fsFreeKb)), style = MaterialTheme.typography.bodyMedium, color = Rack.textSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Bar(f, Rack.fill(d.health), Modifier.fillMaxWidth(), height = 6.dp)
                }
            }

            Chassis {
                DetailRow(stringResource(R.string.label_status), statusText(d.status).asString())
                RowDivider()
                val isDefault = d.warningTemp == null || d.warningTemp == 0
                DetailRow(
                    stringResource(R.string.label_temp_limits),
                    stringResource(if (isDefault) R.string.temp_limits_default else R.string.temp_limits, d.effectiveWarningTemp, d.effectiveCriticalTemp),
                )
                d.isSpinning?.let {
                    RowDivider()
                    DetailRow(stringResource(R.string.label_spinning), stringResource(if (it) R.string.yes else R.string.spun_down))
                }
                RowDivider()
                DetailRow(
                    stringResource(R.string.label_io_errors),
                    (d.numErrors ?: 0).toString(),
                    if ((d.numErrors ?: 0) > 0) Rack.warning else null,
                )
                RowDivider()
                DetailRow(stringResource(R.string.label_reads), formatCount(d.numReads))
                RowDivider()
                DetailRow(stringResource(R.string.label_writes), formatCount(d.numWrites))
                d.transport?.let {
                    RowDivider()
                    DetailRow(stringResource(R.string.label_connection), it.uppercase())
                }
                RowDivider()
                DetailRow(stringResource(R.string.label_identification), d.identification)
            }

            if (physical != null) {
                SectionLabel("SMART")
                Chassis {
                    DetailRow(
                        stringResource(R.string.label_smart_status),
                        smartText(physical.smartStatus),
                        if (physical.smartStatus == "OK") Rack.ok else null,
                    )
                    RowDivider()
                    DetailRow(stringResource(R.string.label_model), listOfNotNull(physical.vendor, physical.name).joinToString(" "))
                    physical.serial?.let { RowDivider(); DetailRow(stringResource(R.string.label_serial), it) }
                    physical.firmware?.let { RowDivider(); DetailRow(stringResource(R.string.label_firmware), it) }
                    physical.interfaceType?.let { RowDivider(); DetailRow(stringResource(R.string.label_interface), it) }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color? = null) {
    ChassisRow(verticalPadding = 11.dp) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Rack.textSecondary, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: Rack.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.4f, fill = false),
        )
    }
}

@Composable
private fun smartText(s: String?): String = when (s) {
    "OK" -> "OK"
    "UNKNOWN", null -> stringResource(R.string.unknown)
    else -> s
}

// ---------------------------------------------------------------------------
// SMART op verzoek
// ---------------------------------------------------------------------------

@Composable
private fun SmartPanel(smart: Load<List<PhysicalDiskInfo>>, overview: DiskOverview, onLoad: () -> Unit) {
    Panel {
        Text(stringResource(R.string.smart_details), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.smart_explainer), style = MaterialTheme.typography.bodySmall, color = Rack.textSecondary)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onLoad,
                enabled = !smart.loading,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Rack.text),
                border = androidx.compose.foundation.BorderStroke(1.dp, Rack.border),
            ) { Text(stringResource(if (smart.data == null) R.string.smart_fetch else R.string.smart_refetch)) }
            Spacer(Modifier.width(12.dp))
            when {
                smart.loading -> SmallSpinner()
                smart.updatedAt != null -> Text(
                    stringResource(R.string.fetched_at, formatTime(smart.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Rack.textSecondary,
                )
            }
        }
        smart.error?.let {
            Spacer(Modifier.height(8.dp))
            InlineError(it.asString())
        }
        val list = smart.data
        if (list != null) {
            val assigned = overview.all.mapNotNull { it.device }.toSet()
            val unassigned = list.filter { it.device.substringAfterLast('/') !in assigned }
            if (unassigned.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.unassigned), style = MaterialTheme.typography.labelMedium, color = Rack.textSecondary)
                unassigned.forEach { d ->
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Led(if (d.smartStatus == "OK") Rack.ok else Rack.ledOff)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(listOfNotNull(d.vendor, d.name).joinToString(" ").ifBlank { d.device }, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(d.device, formatBytes(d.sizeBytes), d.interfaceType).joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = Rack.textSecondary,
                            )
                        }
                        d.temperature?.let { Text("${it.toInt()}°", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Array-bediening: bewust niet op het overzicht, maar achter een knop
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArrayControlsSheet(vm: MainViewModel, data: DiskOverview, onDismiss: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirmArray by remember { mutableStateOf<Boolean?>(null) }
    var confirmParity by remember { mutableStateOf<ParityAction?>(null) }
    val started = data.arrayState == "STARTED"
    val p = data.parity

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Rack.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.array_controls), style = MaterialTheme.typography.headlineMedium)

            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Led(if (started) Rack.ok else Rack.ledOff)
                    Spacer(Modifier.width(10.dp))
                    Text(arrayStateText(data.arrayState).asString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if ("array" in busy) SmallSpinner()
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { confirmArray = !started },
                    enabled = "array" !in busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (started) Rack.tile else Rack.accent,
                        contentColor = if (started) Rack.critical else Rack.onAccent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(if (started) R.string.array_stop_button else R.string.array_start_button), fontWeight = FontWeight.SemiBold) }
            }

            if (started && p != null && data.parities.isNotEmpty()) {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.parity_check), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if ("parity" in busy) SmallSpinner()
                    }
                    Text(p.text.asString(), style = MaterialTheme.typography.bodySmall, color = Rack.textSecondary)
                    if (p.isActive && p.progress != null) {
                        Spacer(Modifier.height(8.dp))
                        Bar(p.progress / 100f, Rack.accent, Modifier.fillMaxWidth(), height = 6.dp)
                    }
                    Spacer(Modifier.height(12.dp))
                    val enabled = "parity" !in busy
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            p.status == "PAUSED" || p.paused == true -> {
                                SheetButton(stringResource(R.string.action_resume), enabled, Modifier.weight(1f)) { vm.parityAction(ParityAction.RESUME) }
                                SheetButton(stringResource(R.string.action_stop), enabled, Modifier.weight(1f), danger = true) { confirmParity = ParityAction.CANCEL }
                            }
                            p.isActive -> {
                                SheetButton(stringResource(R.string.action_pause), enabled, Modifier.weight(1f)) { vm.parityAction(ParityAction.PAUSE) }
                                SheetButton(stringResource(R.string.action_stop), enabled, Modifier.weight(1f), danger = true) { confirmParity = ParityAction.CANCEL }
                            }
                            else -> {
                                SheetButton(stringResource(R.string.parity_check_only), enabled, Modifier.weight(1f)) { confirmParity = ParityAction.START_CHECK }
                                SheetButton(stringResource(R.string.parity_with_corrections), enabled, Modifier.weight(1f)) { confirmParity = ParityAction.START_CORRECT }
                            }
                        }
                    }
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
    confirmParity?.let { action ->
        val (title, text) = when (action) {
            ParityAction.CANCEL -> R.string.parity_cancel_title to R.string.parity_cancel_text
            ParityAction.START_CORRECT -> R.string.parity_dialog_title to R.string.parity_correct_text
            else -> R.string.parity_dialog_title to R.string.parity_check_text
        }
        ConfirmDialog(
            title = stringResource(title),
            text = stringResource(text),
            confirmLabel = stringResource(if (action == ParityAction.CANCEL) R.string.parity_cancel_confirm else R.string.action_start),
            destructive = action == ParityAction.CANCEL,
            onConfirm = { vm.parityAction(action) },
            onDismiss = { confirmParity = null },
        )
    }
}

@Composable
private fun SheetButton(label: String, enabled: Boolean, modifier: Modifier, danger: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) Rack.critical else Rack.text),
        border = androidx.compose.foundation.BorderStroke(1.dp, Rack.border),
    ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

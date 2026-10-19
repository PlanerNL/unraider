package nl.planernl.unraider.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.planernl.unraider.R
import nl.planernl.unraider.data.Dashboard
import nl.planernl.unraider.data.DiskOverview
import nl.planernl.unraider.data.Health
import nl.planernl.unraider.data.arrayStateText
import nl.planernl.unraider.data.asString
import nl.planernl.unraider.data.formatBytes
import nl.planernl.unraider.data.formatKb
import nl.planernl.unraider.ui.AutoRefresh
import nl.planernl.unraider.ui.ErrorState
import nl.planernl.unraider.ui.InlineError
import nl.planernl.unraider.ui.LoadingState
import nl.planernl.unraider.ui.MainViewModel
import nl.planernl.unraider.ui.Pill
import nl.planernl.unraider.ui.SectionCard
import nl.planernl.unraider.ui.StatusDot
import nl.planernl.unraider.ui.UsageBar
import nl.planernl.unraider.ui.formatTime
import nl.planernl.unraider.ui.theme.StatusColors
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: MainViewModel, onOpenDisks: () -> Unit) {
    val state by vm.dashboard.collectAsStateWithLifecycle()
    val disks by vm.disks.collectAsStateWithLifecycle()

    AutoRefresh(15_000) {
        vm.refreshDashboard()
        vm.refreshDisks()
    }

    var pulling by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) { if (!state.loading) pulling = false }

    val errorText = state.error?.asString()
    val data = state.data
    when {
        data == null && errorText != null -> ErrorState(errorText) { vm.refreshDashboard() }
        data == null -> LoadingState()
        else -> PullToRefreshBox(
            isRefreshing = pulling,
            onRefresh = { pulling = true; vm.refreshDashboard(); vm.refreshDisks() },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                errorText?.let { InlineError(stringResource(R.string.refresh_failed, it)) }
                ServerCard(data, state.updatedAt)
                DiskHealthCard(disks.data, onOpenDisks)
                ResourcesCard(data)
                NotificationsCard(data)
                data.partialErrors.forEach { InlineError(it.asString()) }
            }
        }
    }
}

@Composable
private fun ServerCard(d: Dashboard, updatedAt: Long?) {
    SectionCard {
        Text(d.serverName ?: "Unraid", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            listOfNotNull(d.version?.let { "Unraid $it" }, uptimeText(d.bootTime)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.updated_at, formatTime(updatedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiskHealthCard(overview: DiskOverview?, onOpenDisks: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.storage),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenDisks) {
                Text(stringResource(R.string.tab_disks))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            }
        }
        if (overview == null) {
            Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        val stateColor = if (overview.arrayState == "STARTED") StatusColors.ok else StatusColors.critical
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(stringResource(R.string.array_state_pill, arrayStateText(overview.arrayState).asString()), stateColor)
            if (overview.warnings > 0) {
                Pill(pluralStringResource(R.plurals.warnings_count, overview.warnings, overview.warnings), StatusColors.warning)
            }
        }
        val used = overview.capacityUsedKb
        val total = overview.capacityTotalKb
        if (used != null && total != null && total > 0) {
            Spacer(Modifier.height(12.dp))
            UsageBar(used.toFloat() / total)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.used_of, formatKb(used), formatKb(total)), style = MaterialTheme.typography.bodySmall)
        }

        // Compact overzicht: één regel per schijf
        Spacer(Modifier.height(12.dp))
        val sleepingLabel = stringResource(R.string.sleeping).lowercase()
        overview.all.forEach { disk ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(StatusColors.of(disk.health), 8)
                Spacer(Modifier.width(10.dp))
                Text(disk.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    when {
                        disk.temp != null -> "${disk.temp} °C"
                        disk.sleeping -> sleepingLabel
                        else -> "–"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (disk.health == Health.OK || disk.health == Health.SLEEPING)
                        MaterialTheme.colorScheme.onSurfaceVariant else StatusColors.of(disk.health),
                )
                disk.usedFraction?.let {
                    Text(
                        "${(it * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp).padding(start = 8.dp),
                    )
                } ?: Spacer(Modifier.width(48.dp))
            }
        }

        overview.parity?.let { p ->
            Spacer(Modifier.height(8.dp))
            val errors = p.errors?.takeIf { it > 0 }?.let { " · " + pluralStringResource(R.plurals.errors_count, it, it) } ?: ""
            Text(
                stringResource(R.string.parity_line, p.text.asString()) + errors,
                style = MaterialTheme.typography.bodySmall,
                color = if ((p.errors ?: 0) > 0) StatusColors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResourcesCard(d: Dashboard) {
    if (d.cpuPercent == null && d.memPercent == null) return
    SectionCard {
        Text(stringResource(R.string.system), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        d.cpuPercent?.let {
            Spacer(Modifier.height(10.dp))
            Row {
                Text("CPU", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${it.toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            UsageBar((it / 100).toFloat())
        }
        d.memPercent?.let {
            Spacer(Modifier.height(10.dp))
            Row {
                Text(stringResource(R.string.memory), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    if (d.memUsedBytes != null && d.memTotalBytes != null)
                        "${formatBytes(d.memUsedBytes)} / ${formatBytes(d.memTotalBytes)}"
                    else "${it.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            UsageBar((it / 100).toFloat())
        }
    }
}

@Composable
private fun NotificationsCard(d: Dashboard) {
    if (d.unreadAlerts == null && d.unreadWarnings == null) return
    val alerts = d.unreadAlerts ?: 0
    val warnings = d.unreadWarnings ?: 0
    val info = d.unreadInfo ?: 0
    SectionCard {
        Text(stringResource(R.string.notifications_unread), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(
                pluralStringResource(R.plurals.notif_alerts, alerts, alerts),
                if (alerts > 0) StatusColors.critical else StatusColors.sleeping,
            )
            Pill(
                pluralStringResource(R.plurals.warnings_count, warnings, warnings),
                if (warnings > 0) StatusColors.warning else StatusColors.sleeping,
            )
            Pill(stringResource(R.string.notif_info, info), StatusColors.sleeping)
        }
    }
}

/** `uptime` is in de API het opstarttijdstip (ISO); toon dat als looptijd. */
@Composable
private fun uptimeText(boot: String?): String? {
    if (boot.isNullOrBlank()) return null
    val d = try {
        Duration.between(Instant.parse(boot), Instant.now())
    } catch (e: Exception) {
        return null
    }
    val days = d.toDays().toInt()
    val hours = (d.toHours() % 24).toInt()
    return if (days > 0) stringResource(R.string.uptime_days, days, hours)
    else stringResource(R.string.uptime_hours, hours, (d.toMinutes() % 60).toInt())
}

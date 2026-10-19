package nl.planernl.unraider.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.planernl.unraider.R
import nl.planernl.unraider.data.LiveMetrics
import nl.planernl.unraider.data.asString
import nl.planernl.unraider.data.formatBytes
import nl.planernl.unraider.data.formatRate
import nl.planernl.unraider.ui.AutoRefresh
import nl.planernl.unraider.ui.Bar
import nl.planernl.unraider.ui.Chassis
import nl.planernl.unraider.ui.ChassisRow
import nl.planernl.unraider.ui.HeaderIconButton
import nl.planernl.unraider.ui.InlineError
import nl.planernl.unraider.ui.LineChart
import nl.planernl.unraider.ui.MainViewModel
import nl.planernl.unraider.ui.Panel
import nl.planernl.unraider.ui.RowDivider
import nl.planernl.unraider.ui.ScreenHeader
import nl.planernl.unraider.ui.ScreenList
import nl.planernl.unraider.ui.SectionLabel
import nl.planernl.unraider.ui.theme.Rack
import java.time.Duration
import java.time.Instant

@Composable
fun OverviewScreen(
    vm: MainViewModel,
    onOpenStorage: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenWebGui: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val server by vm.server.collectAsStateWithLifecycle()
    val disks by vm.disks.collectAsStateWithLifecycle()
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val metricsError by vm.metricsError.collectAsStateWithLifecycle()

    AutoRefresh(LiveMetrics.INTERVAL_MS) { vm.sampleMetrics() }
    AutoRefresh(30_000) {
        vm.refreshServer()
        vm.refreshDisks()
    }

    var menu by remember { mutableStateOf(false) }
    val info = server.data
    val latest = metrics.latest

    ScreenList(
        loading = server.loading || disks.loading,
        onRefresh = { vm.refreshServer(); vm.refreshDisks(); vm.sampleMetrics() },
    ) {
        item {
            ScreenHeader(
                title = info?.name ?: "Unraid",
                subtitle = listOfNotNull(info?.version?.let { "Unraid $it" }, uptimeText(info?.bootTime))
                    .joinToString(", ")
                    .ifBlank { stringResource(if (server.error != null) R.string.not_reachable else R.string.loading) },
                subtitleLed = when {
                    server.error != null && info == null -> Rack.critical
                    info != null -> Rack.ok
                    else -> Rack.ledOff
                },
            ) {
                HeaderIconButton(
                    Icons.Outlined.Notifications,
                    stringResource(R.string.notifications_title),
                    badge = (info?.unreadTotal ?: 0) > 0,
                    onClick = onOpenNotifications,
                )
                Box {
                    HeaderIconButton(Icons.Outlined.MoreVert, stringResource(R.string.more)) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Rack.chassis) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.tab_webgui)) }, onClick = { menu = false; onOpenWebGui() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { menu = false; onOpenSettings() })
                    }
                }
            }
        }

        server.error?.takeIf { info == null }?.let { e -> item { InlineError(e.asString()) } }

        // Opslag: de belangrijkste status, zonder bedieningsknoppen
        disks.data?.let { d -> item { StorageSummary(d, onClick = onOpenStorage) } }

        // CPU
        item {
            Panel {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("CPU", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(latest?.cpuPercent?.let { "${it.toInt()}%" } ?: "–", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.height(8.dp))
                val series = metrics.cpuSeries
                LineChart(
                    values = series,
                    max = 100f,
                    capacity = LiveMetrics.CAPACITY,
                    description = stringResource(R.string.a11y_cpu_chart, latest?.cpuPercent?.toInt() ?: 0),
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(stringResource(R.string.chart_5min_ago), style = MaterialTheme.typography.bodySmall, color = Rack.textSecondary, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.chart_now), style = MaterialTheme.typography.bodySmall, color = Rack.textSecondary)
                }
            }
        }

        // Geheugen
        val pct = latest?.memPercent
        val memUsed = latest?.memUsedBytes
        val memTotal = latest?.memTotalBytes
        if (pct != null) {
            item {
                Panel {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.memory), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (memUsed != null && memTotal != null) {
                            Text(
                                stringResource(R.string.mem_of, formatBytes(memUsed), formatBytes(memTotal)),
                                style = MaterialTheme.typography.bodySmall,
                                color = Rack.textSecondary,
                                modifier = Modifier.padding(end = 10.dp, bottom = 3.dp),
                            )
                        }
                        Text("${pct.toInt()}%", style = MaterialTheme.typography.headlineSmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    Bar((pct / 100).toFloat(), Rack.accent, Modifier.fillMaxWidth(), height = 6.dp)
                }
            }
        }

        // Temperaturen
        val hottestDisk = disks.data?.all?.filter { it.temp != null }?.maxByOrNull { it.temp ?: 0 }
        val temps = buildList<Triple<String, String, Color>> {
            latest?.cpuTemp?.let { add(Triple("CPU", "${it.toInt()}°", Rack.text)) }
            latest?.boardTemp?.let { add(Triple("board", "${it.toInt()}°", Rack.text)) }
            hottestDisk?.let { add(Triple("disk:" + it.displayName, "${it.temp}°", tempColor(it))) }
        }
        if (temps.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.temperatures)) }
            item {
                Chassis {
                    temps.forEachIndexed { i, (key, value, color) ->
                        if (i > 0) RowDivider()
                        ChassisRow {
                            val label = when {
                                key == "board" -> stringResource(R.string.motherboard)
                                key.startsWith("disk:") -> stringResource(R.string.hottest_disk)
                                else -> key
                            }
                            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (key.startsWith("disk:")) {
                                Text(key.removePrefix("disk:"), style = MaterialTheme.typography.bodySmall, color = Rack.textSecondary)
                            }
                            Text(value, style = MaterialTheme.typography.titleMedium, color = color)
                        }
                    }
                }
            }
        }

        // Netwerk
        if (latest?.rxBytesPerSec != null || latest?.txBytesPerSec != null) {
            item { SectionLabel(stringResource(R.string.network)) }
            item {
                Chassis {
                    ChassisRow {
                        Text(stringResource(R.string.net_in), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(formatRate(latest?.rxBytesPerSec), style = MaterialTheme.typography.titleMedium)
                    }
                    RowDivider()
                    ChassisRow {
                        Text(stringResource(R.string.net_out), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(formatRate(latest?.txBytesPerSec), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        metricsError?.let { e -> item { InlineError(stringResource(R.string.live_failed, e.asString())) } }
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

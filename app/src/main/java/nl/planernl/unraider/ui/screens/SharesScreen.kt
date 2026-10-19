package nl.planernl.unraider.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.planernl.unraider.R
import nl.planernl.unraider.data.ShareInfo
import nl.planernl.unraider.data.asString
import nl.planernl.unraider.data.formatKb
import nl.planernl.unraider.ui.AutoRefresh
import nl.planernl.unraider.ui.Bar
import nl.planernl.unraider.ui.Chassis
import nl.planernl.unraider.ui.ChassisRow
import nl.planernl.unraider.ui.ErrorState
import nl.planernl.unraider.ui.InlineError
import nl.planernl.unraider.ui.LoadingState
import nl.planernl.unraider.ui.MainViewModel
import nl.planernl.unraider.ui.RowDivider
import nl.planernl.unraider.ui.ScreenHeader
import nl.planernl.unraider.ui.ScreenList
import nl.planernl.unraider.ui.SectionLabel
import nl.planernl.unraider.ui.theme.Rack

@Composable
fun SharesScreen(vm: MainViewModel) {
    val state by vm.shares.collectAsStateWithLifecycle()
    AutoRefresh(60_000) { vm.refreshShares() }

    val error = state.error?.asString()
    val list = state.data
    when {
        list == null && error != null -> ErrorState(error) { vm.refreshShares() }
        list == null -> LoadingState()
        else -> ScreenList(loading = state.loading, onRefresh = { vm.refreshShares() }) {
            val total = list.sumOf { it.usedKb ?: 0L }
            item {
                ScreenHeader(
                    title = stringResource(R.string.tab_shares),
                    subtitle = pluralStringResource(R.plurals.shares_summary, list.size, list.size, formatKb(total)),
                )
            }
            error?.let { item { InlineError(stringResource(R.string.refresh_failed, it)) } }
            if (list.isEmpty()) {
                item {
                    Text(stringResource(R.string.no_shares), style = MaterialTheme.typography.bodyMedium, color = Rack.textSecondary)
                }
            } else {
                val largest = list.maxOf { it.usedKb ?: 0L }.coerceAtLeast(1L)
                item { SectionLabel(stringResource(R.string.shares_size_location)) }
                item {
                    Chassis {
                        list.forEachIndexed { i, s ->
                            if (i > 0) RowDivider()
                            ShareRow(s, largest)
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.shares_bar_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Rack.textSecondary,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareRow(s: ShareInfo, largest: Long) {
    ChassisRow {
        Column(Modifier.weight(1f)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val location = when (s.usesCache) {
                true -> stringResource(R.string.share_uses_cache)
                false -> stringResource(R.string.share_array_only)
                null -> null
            }
            val sub = listOfNotNull(s.comment, location).joinToString(", ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Rack.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Bar(((s.usedKb ?: 0L).toFloat() / largest), Rack.accent, Modifier.fillMaxWidth())
        }
        Column(Modifier.width(76.dp)) {
            Text(formatKb(s.usedKb), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            s.freeKb?.let {
                Text(
                    stringResource(R.string.size_free, formatKb(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Rack.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

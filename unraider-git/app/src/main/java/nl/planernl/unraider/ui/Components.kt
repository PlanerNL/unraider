package nl.planernl.unraider.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay
import nl.planernl.unraider.R
import nl.planernl.unraider.data.LocalIconLoader
import nl.planernl.unraider.ui.theme.Rack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Opbouw van een scherm
// ---------------------------------------------------------------------------

/** Paginakop: grote titel in Barlow Condensed, met regel eronder en ruimte voor knoppen. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    subtitleLed: Color? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    if (subtitleLed != null) {
                        Led(subtitleLed)
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Rack.textSecondary)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Klein kopje boven een groep. */
@Composable
fun SectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 6.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Rack.textSecondary, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = Rack.textSecondary)
    }
}

/** Eén "chassis": een vlak waarin rijen onder elkaar staan, gescheiden door dunne lijnen. */
@Composable
fun Chassis(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Rack.chassis)
            .border(1.dp, Rack.border, RoundedCornerShape(14.dp)),
        content = content,
    )
}

/** Losse kaart met binnenruimte (voor samenvattingen en grafieken). */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Chassis(modifier) { Column(Modifier.padding(14.dp), content = content) }
}

/** Scheidingslijn tussen rijen in een chassis. */
@Composable
fun RowDivider() = HorizontalDivider(color = Rack.divider, thickness = 1.dp)

/** Een rij in een chassis. Gebruik [RowDivider] tussen rijen. */
@Composable
fun ChassisRow(
    onClick: (() -> Unit)? = null,
    verticalPadding: Dp = 12.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

// ---------------------------------------------------------------------------
// Kleine onderdelen
// ---------------------------------------------------------------------------

@Composable
fun Led(color: Color, size: Dp = 7.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Dun gebruiksbalkje. */
@Composable
fun Bar(fraction: Float, color: Color = Rack.accent, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Rack.track)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(color)
        )
    }
}

/** Balk die per schijf een stuk toont: breedte = capaciteit, vulling = gebruik. */
data class BarSegment(val weight: Float, val fraction: Float, val color: Color)

@Composable
fun SegmentedBar(segments: List<BarSegment>, description: String, modifier: Modifier = Modifier) {
    if (segments.isEmpty()) return
    Row(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        segments.forEach { s ->
            Bar(s.fraction, s.color, Modifier.weight(s.weight.coerceAtLeast(0.01f)), height = 6.dp)
        }
    }
}

/** Icoontegel met optionele afbeelding (Docker-template of plugin) en statusstip. */
@Composable
fun IconTile(
    imageUrl: String?,
    fallback: ImageVector,
    statusColor: Color? = null,
    dimmed: Boolean = false,
) {
    Box(Modifier.size(38.dp)) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Rack.tile),
            contentAlignment = Alignment.Center,
        ) {
            val alpha = if (dimmed) 0.5f else 1f
            val loader = LocalIconLoader.current
            if (imageUrl != null && loader != null) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    imageLoader = loader,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(28.dp),
                    alpha = alpha,
                    loading = { FallbackIcon(fallback, alpha) },
                    error = { FallbackIcon(fallback, alpha) },
                )
            } else {
                FallbackIcon(fallback, alpha)
            }
        }
        if (statusColor != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Rack.chassis)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
        }
    }
}

@Composable
private fun FallbackIcon(icon: ImageVector, alpha: Float) {
    Icon(icon, contentDescription = null, tint = Rack.iconSoft.copy(alpha = alpha), modifier = Modifier.size(20.dp))
}

/** Aan/uit-schakelaar in rack-kleuren. */
@Composable
fun RackSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true, label: String) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Rack.text,
            checkedTrackColor = Rack.accent,
            checkedBorderColor = Rack.accent,
            uncheckedThumbColor = Rack.textSecondary,
            uncheckedTrackColor = Rack.track,
            uncheckedBorderColor = Rack.track,
        ),
    )
}

/** Twee- of meerdelige schakelaar (Docker | Plugins). */
@Composable
fun SegmentedControl(options: List<Pair<String, String?>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Rack.chassis)
            .border(1.dp, Rack.border, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, (label, count) ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) Rack.segmentActive else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Row {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) Rack.text else Rack.textSecondary)
                    if (count != null) {
                        Spacer(Modifier.size(6.dp))
                        Text(count, style = MaterialTheme.typography.labelLarge, color = Rack.textSecondary)
                    }
                }
            }
        }
    }
}

/** Ronde knop met icoon (bel, menu). */
@Composable
fun HeaderIconButton(icon: ImageVector, description: String, badge: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Rack.chassis)
            .border(1.dp, Rack.border, RoundedCornerShape(12.dp))
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Rack.text, modifier = Modifier.size(20.dp))
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 10.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Rack.accent)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Grafiek
// ---------------------------------------------------------------------------

/**
 * Lijngrafiek met lichte vulling. [values] zijn 0..[max]; de laatste waarde staat rechts.
 * Bij weinig punten wordt de grafiek vanaf rechts opgebouwd, zodat hij "binnenloopt".
 */
@Composable
fun LineChart(
    values: List<Float>,
    max: Float,
    capacity: Int,
    color: Color = Rack.accent,
    modifier: Modifier = Modifier,
    description: String,
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(70.dp)
            .semantics { contentDescription = description }
    ) {
        val midY = size.height / 2
        drawLine(Rack.divider, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.dp.toPx())
        if (values.size < 2 || max <= 0f) return@Canvas
        val step = size.width / (capacity - 1).coerceAtLeast(1)
        val startX = size.width - step * (values.size - 1)
        fun y(v: Float) = size.height - (v / max).coerceIn(0f, 1f) * (size.height - 2.dp.toPx())

        val line = Path()
        values.forEachIndexed { i, v ->
            val x = startX + i * step
            if (i == 0) line.moveTo(x, y(v)) else line.lineTo(x, y(v))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(startX, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.02f))))
        drawPath(line, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------------------
// Toestanden
// ---------------------------------------------------------------------------

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Rack.textSecondary, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Rack.textSecondary)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Rack.accent, contentColor = Rack.onAccent),
        ) { Text(stringResource(R.string.action_retry), fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Rack.accent, strokeWidth = 2.dp)
    }
}

@Composable
fun SmallSpinner() = CircularProgressIndicator(Modifier.size(18.dp), color = Rack.accent, strokeWidth = 2.dp)

/** Smalle foutregel boven bestaande gegevens. */
@Composable
fun InlineError(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Rack.critical.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Led(Rack.critical)
        Spacer(Modifier.size(10.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = Rack.text)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Rack.chassis,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = Rack.textSecondary) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) Rack.critical else Rack.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = Rack.textSecondary) }
        },
    )
}

/** Ververst periodiek zolang het scherm zichtbaar is en de app op de voorgrond staat. */
@Composable
fun AutoRefresh(intervalMs: Long, onTick: () -> Unit) {
    val tick by rememberUpdatedState(onTick)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, intervalMs) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                tick()
                delay(intervalMs)
            }
        }
    }
}

/** Vult de ruimte onder de laatste lijst zodat de navigatiebalk niets afdekt. */
@Composable
fun BottomSpacer() = Spacer(Modifier.height(16.dp))

fun formatTime(millis: Long?): String =
    millis?.let { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) } ?: "–"

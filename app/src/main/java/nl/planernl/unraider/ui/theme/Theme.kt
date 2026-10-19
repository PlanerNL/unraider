package nl.planernl.unraider.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import nl.planernl.unraider.R
import nl.planernl.unraider.data.Health

/** Rustige rack-stijl: mat donkergrijs, oranje alleen als accent. */
object Rack {
    val background = Color(0xFF141619)
    val chassis = Color(0xFF1B1E22)
    val border = Color(0xFF262A2F)
    val divider = Color(0xFF24282D)
    val track = Color(0xFF2A2E34)
    val tile = Color(0xFF262A30)
    val segmentActive = Color(0xFF2E3238)

    val text = Color(0xFFE6E4E1)
    val textSecondary = Color(0xFF8E949B)
    val iconSoft = Color(0xFFCFC9C3)

    val accent = Color(0xFFF26B2A)
    val accentLight = Color(0xFFFF915C)
    val onAccent = Color(0xFF1A0A04)

    val ok = Color(0xFF4ADE80)
    val warning = Color(0xFFF5C443)
    val critical = Color(0xFFEF4444)
    val sleeping = Color(0xFF5B6068)
    val ledOff = Color(0xFF3A3F46)

    fun led(h: Health): Color = when (h) {
        Health.OK -> ok
        Health.SLEEPING -> ledOff
        Health.WARNING -> warning
        Health.CRITICAL -> critical
    }

    /** Kleur voor balkjes en temperatuur: oranje bij in orde, statuskleur bij problemen. */
    fun fill(h: Health): Color = when (h) {
        Health.OK -> accent
        Health.SLEEPING -> sleeping
        Health.WARNING -> warning
        Health.CRITICAL -> critical
    }
}

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

val BarlowCondensed = FontFamily(Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold))

private val base = TextStyle(fontFamily = Barlow, color = Rack.text)

private val RackTypography = Typography(
    headlineMedium = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 33.sp),
    headlineSmall = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp),
    titleLarge = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = base.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = base.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp),
)

private val RackColors = darkColorScheme(
    primary = Rack.accent,
    onPrimary = Rack.onAccent,
    secondary = Rack.accentLight,
    background = Rack.background,
    onBackground = Rack.text,
    surface = Rack.background,
    onSurface = Rack.text,
    surfaceVariant = Rack.chassis,
    onSurfaceVariant = Rack.textSecondary,
    surfaceContainerLowest = Rack.background,
    surfaceContainerLow = Rack.chassis,
    surfaceContainer = Rack.chassis,
    surfaceContainerHigh = Rack.tile,
    surfaceContainerHighest = Rack.segmentActive,
    outline = Rack.border,
    outlineVariant = Rack.divider,
    error = Rack.critical,
)

@Composable
fun UnraiderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RackColors, typography = RackTypography, content = content)
}

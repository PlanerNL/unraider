package nl.planernl.unraider.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import nl.planernl.unraider.data.Health

private val UnraidOrange = Color(0xFFF15A2C)

private val Dark = darkColorScheme(
    primary = UnraidOrange,
    onPrimary = Color.White,
    secondary = Color(0xFFFFB59C),
    background = Color(0xFF111316),
    surface = Color(0xFF111316),
    surfaceContainer = Color(0xFF1B1D22),
    surfaceContainerHigh = Color(0xFF23262C),
    surfaceContainerHighest = Color(0xFF2C3037),
    onBackground = Color(0xFFE8EAED),
    onSurface = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFFA9AEB6),
    outlineVariant = Color(0xFF3A3F47),
)

private val Light = lightColorScheme(
    primary = Color(0xFFC6421A),
    onPrimary = Color.White,
    background = Color(0xFFF7F7F8),
    surface = Color(0xFFF7F7F8),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F1F3),
    surfaceContainerHighest = Color(0xFFE6E8EB),
)

@Composable
fun UnraidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}

/** Statuskleuren; vast, zodat groen/oranje/rood in beide thema's hetzelfde betekenen. */
object StatusColors {
    val ok = Color(0xFF34C77B)
    val sleeping = Color(0xFF7D8AA0)
    val warning = Color(0xFFF5A623)
    val critical = Color(0xFFE5484D)

    fun of(h: Health): Color = when (h) {
        Health.OK -> ok
        Health.SLEEPING -> sleeping
        Health.WARNING -> warning
        Health.CRITICAL -> critical
    }
}

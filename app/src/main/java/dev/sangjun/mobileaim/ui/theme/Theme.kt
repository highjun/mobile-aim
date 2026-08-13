package dev.sangjun.mobileaim.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Blue accent drives the controls (slider, icons). The reticle deliberately opts out and stays
// red-on-black for visibility over arbitrary camera scenes — see AimOverlay.
private val MobileAimColorScheme = darkColorScheme(
    primary = Color(0xFF63A4FF),
    onPrimary = Color(0xFF00305F),
    primaryContainer = Color(0xFF004785),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253141),
    background = Color(0xFF0B0B0C),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9199),
)

@Composable
fun MobileAimTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MobileAimColorScheme, content = content)
}

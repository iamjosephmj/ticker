package tech.ssemaj.processclock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The arcade cabinet is always dark — no light scheme, no dynamic color.
 * Matches the Pac-Man design of the README assets.
 */
private val ArcadeColorScheme = darkColorScheme(
    primary = PacYellow,
    onPrimary = ArcadeBlack,
    secondary = MazeBlue,
    tertiary = BlinkyRed,
    background = ArcadeBlack,
    onBackground = ArcadeWhite,
    surface = ArcadeBlack,
    onSurface = ArcadeWhite,
    onSurfaceVariant = ArcadeGrey,
    error = BlinkyRed,
)

@Composable
fun ProcessClockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArcadeColorScheme,
        typography = Typography,
        content = content
    )
}

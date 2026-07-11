package tech.ssemaj.processclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.ssemaj.processclock.ui.PacmanLane
import tech.ssemaj.processclock.ui.theme.ArcadeGrey
import tech.ssemaj.processclock.ui.theme.ArcadeWhite
import tech.ssemaj.processclock.ui.theme.BlinkyRed
import tech.ssemaj.processclock.ui.theme.MazeBlue
import tech.ssemaj.processclock.ui.theme.PacYellow
import tech.ssemaj.processclock.ui.theme.ProcessClockTheme
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Demo of the ticker API as an arcade cabinet: a wall clock whose digits —
 * and Pac-Man — advance only when a kernel-aligned tick lands.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProcessClockTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                ) { innerPadding ->
                    ClockScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun ClockScreen(
    modifier: Modifier = Modifier,
    viewModel: ClockViewModel = viewModel(),
) {
    val tick by viewModel.ticks.collectAsStateWithLifecycle()
    val zoneId by rememberSystemZoneId()
    val formatter = remember(zoneId) {
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zoneId)
    }
    val epochSeconds = tick?.epochSeconds ?: (System.currentTimeMillis() / 1_000)

    // 10 points per dot, i.e. per kernel tick observed by this composition.
    var score by remember { mutableLongStateOf(0L) }
    LaunchedEffect(tick) { if (tick != null) score += 10 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .border(3.dp, MazeBlue, RoundedCornerShape(14.dp))
            .padding(6.dp)
            .border(1.5.dp, MazeBlue, RoundedCornerShape(10.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "HIGH SCORE",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 6.sp,
            color = BlinkyRed,
        )
        Text(
            text = "%06d".format(score),
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            letterSpacing = 4.sp,
            color = ArcadeWhite,
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = formatter.format(Instant.ofEpochSecond(epochSeconds)),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 56.sp,
            letterSpacing = 4.sp,
            color = PacYellow,
            modifier = Modifier.testTag("clock")
        )

        Spacer(Modifier.height(20.dp))

        // One lap = one minute; Pac-Man moves only when a kernel tick lands.
        PacmanLane(secondsOfMinute = (epochSeconds % 60).toInt())

        Spacer(Modifier.height(24.dp))

        tick?.let {
            Text(
                text = "TICK WORK ON ${it.tickThread.uppercase()}" +
                    if (it.onMainThread) " (MAIN!)" else "",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                color = if (it.onMainThread) BlinkyRed else ArcadeGrey,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Blinks with tick parity — even the blink is kernel-driven.
        Text(
            text = "INSERT COIN · 1 HZ · TIMERFD",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 3.sp,
            color = if (epochSeconds % 2 == 0L) PacYellow else ArcadeGrey.copy(alpha = 0.25f),
        )
    }
}

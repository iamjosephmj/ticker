package tech.ssemaj.processclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.ZoneId

/**
 * The device's current [ZoneId], updating if the user changes the timezone
 * while the UI is visible. Epoch seconds from the ticker are zone-independent;
 * only this formatting layer cares.
 */
@Composable
fun rememberSystemZoneId(): State<ZoneId> {
    val context = LocalContext.current
    val zone = remember { mutableStateOf(ZoneId.systemDefault()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                zone.value = ZoneId.systemDefault()
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return zone
}

package ch.rezeptli.app.presentation.common

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Haelt den Bildschirm an, solange dieser Bereich sichtbar ist.
 *
 * Gedacht fuer den Kochmodus: Das Geraet liegt auf der Arbeitsflaeche, die Haende sind
 * teigig, und der Bildschirm soll nicht mitten im Schritt ausgehen. Beim Verlassen wird
 * die Sperre wieder freigegeben, damit sie sich nicht ueber die App hinaus auswirkt.
 */
@Composable
fun KeepScreenOn() {
    val context = LocalContext.current

    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

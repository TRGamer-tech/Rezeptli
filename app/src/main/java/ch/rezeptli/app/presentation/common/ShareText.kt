package ch.rezeptli.app.presentation.common

import android.content.Context
import android.content.Intent

/**
 * Reicht einen Text an das Teilen-Menue des Geraets weiter.
 *
 * Bewusst ohne eigene Auswahl von Apps: Welche Messenger jemand benutzt, geht die App
 * nichts an, und Android kennt sie ohnehin besser.
 */
fun Context.shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, null))
}

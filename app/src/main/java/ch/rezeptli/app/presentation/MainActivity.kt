package ch.rezeptli.app.presentation

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.navigation.RezeptliNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // Die App ist durchgehend dunkel - damit bleiben auch die Symbole in der
        // Statusleiste hell, unabhaengig von der Einstellung des Geraets.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Ein aus dem Browser geteilter Link fuehrt direkt in den Import.
        val sharedUrl = intent.sharedRecipeUrl()

        setContent {
            RezeptliTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RezeptliNavHost(sharedUrl = sharedUrl)
                }
            }
        }
    }

    /** Der geteilte Text, sofern sich darin eine Adresse findet. */
    private fun Intent?.sharedRecipeUrl(): String? {
        if (this?.action != Intent.ACTION_SEND || type != "text/plain") return null
        val text = getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        // Manche Apps teilen "Titel https://..." - dann zaehlt der erste Link darin.
        return text
            .split(WHITESPACE)
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

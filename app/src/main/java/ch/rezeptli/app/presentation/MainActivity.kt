package ch.rezeptli.app.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.navigation.Destinations
import ch.rezeptli.app.presentation.navigation.RezeptliNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Der Startbildschirm bleibt stehen, bis feststeht, wohin die App fuehrt -
        // sonst blitzt die Rezeptliste auf, bevor das Onboarding sie ersetzt.
        installSplashScreen().setKeepOnScreenCondition {
            viewModel.startState.value == StartState.Unbekannt
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Ein aus dem Browser geteilter Link fuehrt direkt in den Import.
        val sharedUrl = intent.sharedRecipeUrl()

        setContent {
            val startState by viewModel.startState.collectAsStateWithLifecycle()

            RezeptliTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (startState != StartState.Unbekannt) {
                        RezeptliNavHost(
                            sharedUrl = sharedUrl,
                            startDestination = when (startState) {
                                StartState.Onboarding -> Destinations.ONBOARDING
                                else -> Destinations.RECIPE_LIST
                            },
                        )
                    }
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

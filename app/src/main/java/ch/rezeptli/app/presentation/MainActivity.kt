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
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition {
            viewModel.startState.value == StartState.Unbekannt
        }
        // Statt hart zu verschwinden, waechst der Topf ein wenig, waehrend das Startbild
        // ausblendet. Der Animator folgt der Android-Animationsskala von selbst; die
        // App-Einstellung "Bewegung reduzieren" wird hier zusaetzlich beachtet.
        splash.setOnExitAnimationListener { provider ->
            if (viewModel.reduceMotion.value) {
                provider.remove()
                return@setOnExitAnimationListener
            }
            runCatching { provider.iconView }
                .getOrNull()
                ?.animate()
                ?.scaleX(SPLASH_ICON_GROWTH)
                ?.scaleY(SPLASH_ICON_GROWTH)
                ?.setDuration(SPLASH_EXIT_MS)
                ?.start()
            provider.view
                .animate()
                .alpha(0f)
                .setDuration(SPLASH_EXIT_MS)
                .withEndAction { provider.remove() }
                .start()
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Ein aus dem Browser geteilter Link fuehrt direkt in den Import.
        val sharedUrl = intent.sharedRecipeUrl()

        setContent {
            val startState by viewModel.startState.collectAsStateWithLifecycle()
            val reduceMotion by viewModel.reduceMotion.collectAsStateWithLifecycle()

            RezeptliTheme(reduceMotion = reduceMotion) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (startState != StartState.Unbekannt) {
                        RezeptliNavHost(
                            sharedUrl = sharedUrl,
                            startDestination = when (startState) {
                                StartState.Onboarding -> Destinations.ONBOARDING
                                else -> Destinations.PLAN
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
        const val SPLASH_EXIT_MS = 260L
        const val SPLASH_ICON_GROWTH = 1.12f
    }
}

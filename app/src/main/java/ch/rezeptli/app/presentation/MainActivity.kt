package ch.rezeptli.app.presentation

import android.app.UiModeManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.domain.settings.ThemeChoice
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
            viewModel.startState.value == StartState.Unbekannt || viewModel.theme.value == null
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
            val theme by viewModel.theme.collectAsStateWithLifecycle()

            // Hell oder dunkel soll fuer die ganze App gelten, nicht nur fuer das, was Compose
            // zeichnet - auch fuer das Startbild und die Dialoge des Systems.
            LaunchedEffect(theme) { theme?.let(::applyNightMode) }

            RezeptliTheme(theme = theme ?: ThemeChoice.SYSTEM, reduceMotion = reduceMotion) {
                // Die Symbole der Systemleisten richten sich nach dem Design der App, nicht nach
                // dem des Geraets: Wer im dunklen System "Hell" waehlt, saehe sonst weisse
                // Symbole auf cremefarbenem Grund.
                val dunkel = RezeptliTheme.isDark
                DisposableEffect(dunkel) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dunkel },
                        navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dunkel },
                    )
                    onDispose {}
                }

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

    /**
     * Sagt Android, ob die App hell oder dunkel sein will. Das gibt es ab Android 12; davor
     * richtet sich nur der Inhalt nach der Wahl, das Startbild folgt weiter dem Geraet.
     */
    private fun applyNightMode(theme: ThemeChoice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val mode = when (theme) {
            ThemeChoice.HELL -> UiModeManager.MODE_NIGHT_NO
            ThemeChoice.DUNKEL -> UiModeManager.MODE_NIGHT_YES
            ThemeChoice.SYSTEM, ThemeChoice.MATERIAL_YOU -> UiModeManager.MODE_NIGHT_AUTO
        }
        getSystemService(UiModeManager::class.java)?.setApplicationNightMode(mode)
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

        /** Die Schleier hinter der Navigationsleiste - dieselben Werte, die Android selbst nimmt. */
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

package ch.rezeptli.app.presentation.common.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset

/**
 * Wie sich die App bewegt - an einer Stelle festgelegt, damit "weniger Bewegung"
 * auch wirklich ueberall gilt.
 *
 * Die App bewegt sich verspielt: Dinge federn herein, Knoepfe geben unter dem Finger
 * nach, Symbole huepfen. Mit [reduced] faellt all das weg. Was bleibt, sind kurze
 * Blenden - sie zeigen, dass sich etwas geaendert hat, ohne dass sich etwas bewegt.
 * Das entspricht dem, was Menschen mit Gleichgewichts- oder Konzentrationsproblemen
 * hilft: keine Bewegung im Raum, kein Nachschwingen, nichts, was endlos laeuft.
 *
 * Bildschirmcode fragt nie selbst nach Federn oder Dauern, sondern immer hier - sonst
 * vergisst irgendein Bildschirm die Einstellung.
 */
@Immutable
class MotionScheme private constructor(
    /** Wahr, wenn Bewegung reduziert ist - durch Android oder die App-Einstellung. */
    val reduced: Boolean,
) {
    /** Fuer Bewegung im Raum, die sichtbar nachschwingen darf: Karten, Symbole, Erfolge. */
    fun <T> bouncy(): FiniteAnimationSpec<T> = if (reduced) {
        snap()
    } else {
        spring(dampingRatio = Tokens.Spring.BouncyDamping, stiffness = Tokens.Spring.BouncyStiffness)
    }

    /** Fuer knappe Rueckmeldungen ohne viel Nachschwingen: Knoepfe, Leisten. */
    fun <T> snappy(): FiniteAnimationSpec<T> = if (reduced) {
        snap()
    } else {
        spring(dampingRatio = Tokens.Spring.SnappyDamping, stiffness = Tokens.Spring.SnappyStiffness)
    }

    /** Fuer grosse Flaechen, die gleiten statt zu federn. */
    fun <T> gentle(): FiniteAnimationSpec<T> = if (reduced) {
        snap()
    } else {
        spring(dampingRatio = Tokens.Spring.GentleDamping, stiffness = Tokens.Spring.GentleStiffness)
    }

    /**
     * Fuer Deckkraft und Farbe. Blenden bleiben auch bei reduzierter Bewegung - nur
     * kuerzer -, weil ein harter Sprung schwerer zu verfolgen ist als eine Blende.
     */
    fun <T> fade(): FiniteAnimationSpec<T> = tween(
        durationMillis = if (reduced) Tokens.Duration.Instant else Tokens.Duration.Base,
        easing = Tokens.Ease.Standard,
    )

    /** Fortschrittsbalken und Zahlen, die hochzaehlen. */
    fun <T> progress(): FiniteAnimationSpec<T> = if (reduced) {
        snap()
    } else {
        tween(durationMillis = Tokens.Duration.Slow, easing = Tokens.Ease.Standard)
    }

    /** Verzoegerung fuer den n-ten Eintrag einer Liste, die nacheinander hereinkommt. */
    fun staggerDelay(index: Int): Int = if (reduced) 0 else index.coerceIn(0, MAX_STAGGERED) * Tokens.Duration.Stagger

    /** Etwas taucht auf: steigt leicht auf und federt in seine Groesse. */
    fun enter(): EnterTransition = if (reduced) {
        fadeIn(fade())
    } else {
        fadeIn(fade()) +
            slideInVertically(offsetSpring()) { it / RISE_FRACTION } +
            scaleIn(bouncy(), initialScale = ENTER_SCALE)
    }

    /** Etwas verschwindet: sinkt leicht ab und blendet aus. */
    fun exit(): ExitTransition = if (reduced) {
        fadeOut(fade())
    } else {
        fadeOut(tween(Tokens.Duration.Fast, easing = Tokens.Ease.Exit)) +
            slideOutVertically(offsetSpring()) { it / RISE_FRACTION } +
            scaleOut(tween(Tokens.Duration.Fast, easing = Tokens.Ease.Exit), targetScale = ENTER_SCALE)
    }

    /** Fuer Verschiebungen ganzer Flaechen - Bildschirmwechsel, Leisten, Schritte. */
    fun offsetSpring(): FiniteAnimationSpec<IntOffset> = if (reduced) {
        snap()
    } else {
        spring(
            dampingRatio = Tokens.Spring.SnappyDamping,
            stiffness = Tokens.Spring.BouncyStiffness,
            visibilityThreshold = IntOffset.VisibilityThreshold,
        )
    }

    companion object {
        val Full = MotionScheme(reduced = false)
        val Reduced = MotionScheme(reduced = true)

        /** Ab hier kommen Eintraege gemeinsam - sonst wartet der zwanzigste eine Sekunde. */
        private const val MAX_STAGGERED = 8
        private const val RISE_FRACTION = 5
        private const val ENTER_SCALE = 0.94f
    }
}

internal val LocalMotion = staticCompositionLocalOf { MotionScheme.Full }

/**
 * Ob Android Animationen entfernt hat (Bedienungshilfen > "Animationen entfernen", oder
 * die Animationsskala in den Entwickleroptionen auf aus).
 *
 * Die Einstellung wird beobachtet, nicht nur einmal gelesen: Wer sie umstellt, waehrend
 * die App offen ist, soll das sofort sehen.
 */
@Composable
internal fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(animationsRemoved(context)) }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = animationsRemoved(context)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun animationsRemoved(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

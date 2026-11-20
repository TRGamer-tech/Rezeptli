package ch.rezeptli.app.presentation.common.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.presentation.common.theme.MotionScheme
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Wie sich ein Symbol bewegt, wenn es erscheint oder wenn sich etwas an ihm aendert. */
enum class IconMotion {
    /** Wird kurz groesser und federt zurueck - fuer Erfolge und Zaehler. */
    POP,

    /** Wackelt hin und her - fuer den Warenkorb, die Karten, Aufmerksamkeit. */
    WIGGLE,

    /** Dreht sich einmal ganz - fuer das Zahnrad und "neu laden". */
    SPIN,

    /** Huepft nach oben - fuer Buecher, Toepfe, alles mit Gewicht. */
    HOP,

    /** Klappt um die senkrechte Achse - fuer den Kalender, das Umblaettern. */
    FLIP,

    /** Schiebt sich seitlich hin und her - fuer Regler. */
    SLIDE,
}

/**
 * Ein Symbol, das sich bewegt: einmal beim Erscheinen (wenn [playOnAppear]) und jedes
 * Mal, wenn sich [trigger] aendert. Wechselt [trigger] auf `null`, bleibt es ruhig - so
 * bewegt sich etwa ein Tab nur beim Auswaehlen, nicht beim Abwaehlen.
 *
 * Bei reduzierter Bewegung steht das Symbol einfach still da - was es bedeutet, aendert
 * sich dadurch nicht.
 */
@Composable
fun MotionIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    motion: IconMotion,
    modifier: Modifier = Modifier,
    trigger: Any? = Unit,
    playOnAppear: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    val scheme = RezeptliTheme.motion
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val offset = remember { Animatable(0f) }
    val flip = remember { Animatable(0f) }
    val erstesMal = remember { FirstRun() }

    LaunchedEffect(trigger) {
        val zumErstenMal = erstesMal.consume()
        if (scheme.reduced || trigger == null) return@LaunchedEffect
        if (zumErstenMal && !playOnAppear) return@LaunchedEffect
        play(motion, scale, rotation, offset, flip, scheme)
    }

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            rotationZ = rotation.value
            rotationY = flip.value
            translationY = offset.value * HOP_HEIGHT.toPx()
            cameraDistance = CAMERA_DISTANCE * density
        },
    )
}

private suspend fun play(
    motion: IconMotion,
    scale: Animatable<Float, AnimationVector1D>,
    rotation: Animatable<Float, AnimationVector1D>,
    offset: Animatable<Float, AnimationVector1D>,
    flip: Animatable<Float, AnimationVector1D>,
    scheme: MotionScheme,
) {
    coroutineScope { playIn(motion, scale, rotation, offset, flip, scheme) }
}

private suspend fun CoroutineScope.playIn(
    motion: IconMotion,
    scale: Animatable<Float, AnimationVector1D>,
    rotation: Animatable<Float, AnimationVector1D>,
    offset: Animatable<Float, AnimationVector1D>,
    flip: Animatable<Float, AnimationVector1D>,
    scheme: MotionScheme,
) {
    when (motion) {
        IconMotion.POP -> {
            scale.animateTo(POP_SCALE, scheme.snappy())
            scale.animateTo(1f, scheme.bouncy())
        }

        IconMotion.WIGGLE -> {
            WIGGLE_ANGLES.forEach { rotation.animateTo(it, scheme.snappy()) }
            rotation.animateTo(0f, scheme.bouncy())
        }

        IconMotion.SPIN -> {
            rotation.snapTo(0f)
            rotation.animateTo(FULL_TURN, tween(SPIN_MS))
            rotation.snapTo(0f)
        }

        IconMotion.HOP -> {
            launch {
                scale.animateTo(HOP_SQUASH, scheme.snappy())
                scale.animateTo(1f, scheme.bouncy())
            }
            offset.animateTo(-1f, scheme.snappy())
            offset.animateTo(0f, scheme.bouncy())
        }

        IconMotion.FLIP -> {
            flip.snapTo(0f)
            flip.animateTo(FULL_TURN, tween(FLIP_MS))
            flip.snapTo(0f)
        }

        IconMotion.SLIDE -> {
            launch {
                scale.animateTo(POP_SCALE, scheme.snappy())
                scale.animateTo(1f, scheme.bouncy())
            }
            rotation.animateTo(SLIDE_TILT, scheme.snappy())
            rotation.animateTo(0f, scheme.bouncy())
        }
    }
}

/**
 * Das Herz fuer "gefaellt mir": gefuellt, sobald [liked], und dabei springt es auf.
 */
@Composable
fun HeartIcon(
    liked: Boolean,
    filledIcon: ImageVector,
    outlinedIcon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = RezeptliTheme.accents.tomato,
) {
    val scheme = RezeptliTheme.motion
    AnimatedContent(
        targetState = liked,
        transitionSpec = {
            if (scheme.reduced) {
                fadeIn(scheme.fade()) togetherWith fadeOut(scheme.fade())
            } else {
                (scaleIn(scheme.bouncy(), initialScale = HEART_START) + fadeIn(scheme.fade())) togetherWith
                    (scaleOut(scheme.snappy(), targetScale = HEART_START) + fadeOut(scheme.fade()))
            }
        },
        contentAlignment = Alignment.Center,
        modifier = modifier,
        label = "herz",
    ) { istGemocht ->
        Icon(
            imageVector = if (istGemocht) filledIcon else outlinedIcon,
            contentDescription = contentDescription,
            tint = if (istGemocht) tint else LocalContentColor.current,
        )
    }
}

/**
 * Ein Symbol, das sich dreht, solange [spinning] - etwa "Neu laden", waehrend gesucht wird.
 * Bei reduzierter Bewegung steht es still; der Knopf zeigt den Zustand dann ueber seine
 * Farbe (deaktiviert).
 */
@Composable
fun SpinningIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    spinning: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val scheme = RezeptliTheme.motion
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (spinning && !scheme.reduced) {
            val transition = rememberInfiniteTransition(label = "drehen")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = FULL_TURN,
                animationSpec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing), RepeatMode.Restart),
                label = "winkel",
            )
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.graphicsLayer { rotationZ = angle },
            )
        } else {
            Icon(imageVector = imageVector, contentDescription = contentDescription, tint = tint)
        }
    }
}

private const val POP_SCALE = 1.28f
private const val HOP_SQUASH = 0.9f
private const val FULL_TURN = 360f
private const val SPIN_MS = 700
private const val FLIP_MS = 560
private const val SLIDE_TILT = 16f
private const val HEART_START = 0.3f
private const val CAMERA_DISTANCE = 12f
private val HOP_HEIGHT = 6.dp
private val WIGGLE_ANGLES = listOf(-16f, 13f, -9f, 5f)

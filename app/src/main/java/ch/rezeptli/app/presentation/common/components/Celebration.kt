package ch.rezeptli.app.presentation.common.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ein kurzer Konfettiregen - fuer die Momente, in denen etwas geklappt hat: gemeinsame
 * Treffer, ein fertiger Wochenplan.
 *
 * Er faellt einmal, wenn sich [trigger] auf einen neuen Wert aendert, und liegt dabei
 * ueber dem Inhalt, ohne Beruehrungen abzufangen. `null` loest nichts aus.
 *
 * Bei reduzierter Bewegung faellt kein Konfetti - es ist reine Dekoration.
 */
@Composable
fun ConfettiBurst(
    trigger: Any?,
    modifier: Modifier = Modifier,
    pieces: Int = DEFAULT_PIECES,
) {
    val motion = RezeptliTheme.motion
    if (motion.reduced || trigger == null) return

    val accents = RezeptliTheme.accents
    val farben = listOf(
        accents.tomato,
        accents.butter,
        accents.herb,
        MaterialTheme.colorScheme.primaryContainer,
        RezeptliTheme.tags.lake.content,
    )
    val teile = remember(trigger, pieces) { confettiFor(trigger.hashCode(), pieces, farben.size) }
    val progress = remember(trigger) { Animatable(0f) }

    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(BURST_MS, easing = LinearEasing))
    }

    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas

        val origin = Offset(size.width / 2f, size.height * ORIGIN_HEIGHT)
        val reach = size.minDimension
        teile.forEach { teil ->
            // Wurfparabel: erst schnell nach oben und aussen, dann faellt alles.
            val x = origin.x + cos(teil.angle) * teil.speed * reach * t
            val y = origin.y + sin(teil.angle) * teil.speed * reach * t + GRAVITY * reach * t * t
            val alpha = (1f - t * t).coerceIn(0f, 1f)
            val farbe: Color = farben[teil.colorIndex].copy(alpha = alpha)
            val breite = teil.size * reach
            rotate(degrees = teil.spin * t, pivot = Offset(x, y)) {
                if (teil.round) {
                    drawCircle(color = farbe, radius = breite / 2f, center = Offset(x, y))
                } else {
                    drawRect(
                        color = farbe,
                        topLeft = Offset(x - breite / 2f, y - breite / 4f),
                        size = Size(breite, breite / 2f),
                    )
                }
            }
        }
    }
}

private class Confetto(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val size: Float,
    val colorIndex: Int,
    val round: Boolean,
)

/** Gleicher Ausloeser, gleiches Bild - der Regen soll beim Neuzeichnen nicht springen. */
private fun confettiFor(seed: Int, count: Int, colors: Int): List<Confetto> {
    val random = Random(seed)
    return List(count) {
        // Winkel nach oben (in Bildschirmkoordinaten negativ), leicht gestreut.
        val angle = (-Math.PI / 2 + (random.nextFloat() - 0.5f) * SPREAD).toFloat()
        Confetto(
            angle = angle,
            speed = MIN_SPEED + random.nextFloat() * (MAX_SPEED - MIN_SPEED),
            spin = (random.nextFloat() - 0.5f) * MAX_SPIN,
            size = MIN_SIZE + random.nextFloat() * (MAX_SIZE - MIN_SIZE),
            colorIndex = random.nextInt(colors),
            round = random.nextBoolean(),
        )
    }
}

private const val DEFAULT_PIECES = 42
private const val BURST_MS = 1600
private const val ORIGIN_HEIGHT = 0.45f
private const val GRAVITY = 1.3f
private const val SPREAD = 2.4f
private const val MIN_SPEED = 0.55f
private const val MAX_SPEED = 1.15f
private const val MAX_SPIN = 900f
private const val MIN_SIZE = 0.016f
private const val MAX_SIZE = 0.03f

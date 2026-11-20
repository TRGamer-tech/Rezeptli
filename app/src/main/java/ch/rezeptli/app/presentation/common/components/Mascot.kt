package ch.rezeptli.app.presentation.common.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * Der Kochtopf der App - dasselbe Motiv wie im App-Symbol, hier lebendig: Dampf steigt
 * auf, und ab und zu hebt sich der Deckel.
 *
 * Bei reduzierter Bewegung (oder mit [animated] = false) steht der Topf still, mit
 * ruhendem Dampf - er sieht dann aus wie das App-Symbol.
 */
@Composable
fun CookingPot(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    animated: Boolean = true,
    potColor: Color = MaterialTheme.colorScheme.primary,
    steamColor: Color = RezeptliTheme.accents.butter,
    heartColor: Color = RezeptliTheme.accents.tomato,
) {
    val bewegt = animated && !RezeptliTheme.motion.reduced

    if (bewegt) {
        val transition = rememberInfiniteTransition(label = "topf")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(STEAM_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "dampf",
        )
        val lid by transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = LID_CYCLE_MS
                    0f at 0
                    0f at LID_REST_MS
                    -1f at LID_REST_MS + 110
                    0f at LID_REST_MS + 220
                    -0.6f at LID_REST_MS + 320
                    0f at LID_REST_MS + 420
                },
                RepeatMode.Restart,
            ),
            label = "deckel",
        )
        Canvas(modifier = modifier.size(size).clearAndSetSemantics { }) {
            drawPot(phase = phase, lidLift = lid, potColor = potColor, steamColor = steamColor, heartColor = heartColor)
        }
    } else {
        Canvas(modifier = modifier.size(size).clearAndSetSemantics { }) {
            drawPot(phase = null, lidLift = 0f, potColor = potColor, steamColor = steamColor, heartColor = heartColor)
        }
    }
}

/**
 * Zeichnet den Topf in einem gedachten Raster von 100 x 100.
 *
 * [phase] laeuft von 0 bis 1 und schiebt die Dampffaeden nach oben; `null` heisst Stillstand.
 */
private fun DrawScope.drawPot(
    phase: Float?,
    lidLift: Float,
    potColor: Color,
    steamColor: Color,
    heartColor: Color,
) {
    val u = size.minDimension / GRID

    // Dampf - drei Faeden, jeder in seinem eigenen Takt.
    STEAM_X.forEachIndexed { index, x ->
        val t = phase?.let { (it + index / STEAM_X.size.toFloat()) % 1f }
        val rise = t?.let { it * STEAM_RISE } ?: 0f
        val alpha = t?.let { sin(PI * it).toFloat().coerceIn(0f, 1f) } ?: 1f
        val sway = t?.let { sin(2 * PI * it).toFloat() * STEAM_SWAY } ?: 0f
        val path = Path().apply {
            moveTo((x + sway) * u, (STEAM_BOTTOM - rise) * u)
            cubicTo(
                (x - STEAM_CURL + sway) * u,
                (STEAM_BOTTOM - 5f - rise) * u,
                (x + STEAM_CURL + sway) * u,
                (STEAM_BOTTOM - 10f - rise) * u,
                (x + sway) * u,
                (STEAM_BOTTOM - 15f - rise) * u,
            )
        }
        drawPath(
            path = path,
            color = steamColor.copy(alpha = alpha),
            style = Stroke(width = 4f * u, cap = StrokeCap.Round),
        )
    }

    // Griffe
    drawRoundRect(
        color = potColor,
        topLeft = Offset(12f * u, 56f * u),
        size = Size(12f * u, 9f * u),
        cornerRadius = CornerRadius(4.5f * u),
    )
    drawRoundRect(
        color = potColor,
        topLeft = Offset(76f * u, 56f * u),
        size = Size(12f * u, 9f * u),
        cornerRadius = CornerRadius(4.5f * u),
    )

    // Topf
    val body = Path().apply {
        addRoundRect(
            RoundRect(
                left = 20f * u,
                top = 51f * u,
                right = 80f * u,
                bottom = 84f * u,
                topLeftCornerRadius = CornerRadius(3f * u),
                topRightCornerRadius = CornerRadius(3f * u),
                bottomLeftCornerRadius = CornerRadius(14f * u),
                bottomRightCornerRadius = CornerRadius(14f * u),
            ),
        )
    }
    drawPath(path = body, color = potColor)

    // Herz auf dem Bauch des Topfs
    val heart = Path().apply {
        val cx = 50f * u
        val cy = 67f * u
        val w = 13f * u
        moveTo(cx, cy + 0.36f * w)
        cubicTo(cx - 0.62f * w, cy - 0.08f * w, cx - 0.26f * w, cy - 0.58f * w, cx, cy - 0.2f * w)
        cubicTo(cx + 0.26f * w, cy - 0.58f * w, cx + 0.62f * w, cy - 0.08f * w, cx, cy + 0.36f * w)
        close()
    }
    drawPath(path = heart, color = heartColor)

    // Deckel mit Knauf - hebt sich und kippt ein wenig, wenn es brodelt.
    translate(top = lidLift * LID_LIFT * u) {
        rotate(degrees = lidLift * LID_TILT, pivot = Offset(50f * u, 46f * u)) {
            drawRoundRect(
                color = potColor,
                topLeft = Offset(16f * u, 42f * u),
                size = Size(68f * u, 8f * u),
                cornerRadius = CornerRadius(4f * u),
            )
            drawCircle(color = potColor, radius = 4.5f * u, center = Offset(50f * u, 37.5f * u))
        }
    }
}

/**
 * Der Ladezustand der App: der brodelnde Topf, darunter ein kurzer Satz.
 *
 * Ersetzt den drehenden Kreis auf ganzen Bildschirmen. Bei reduzierter Bewegung steht
 * der Topf still, und der Text sagt, dass gearbeitet wird.
 */
@Composable
fun RezeptliLoader(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val text = message ?: stringResource(R.string.laden_einen_moment)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            CookingPot(size = 88.dp)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

private const val GRID = 100f
private val STEAM_X = listOf(37f, 50f, 63f)
private const val STEAM_BOTTOM = 33f
private const val STEAM_RISE = 9f
private const val STEAM_CURL = 4f
private const val STEAM_SWAY = 1.5f
private const val STEAM_CYCLE_MS = 1800
private const val LID_CYCLE_MS = 2600
private const val LID_REST_MS = 1700
private const val LID_LIFT = 3f
private const val LID_TILT = 5f

package ch.rezeptli.app.presentation.common.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import kotlinx.coroutines.delay

/*
 * Die kleinen Bewegungen, die die App lebendig machen - als Modifier, damit jeder
 * Bildschirm sie gleich verwendet und keiner die reduzierte Bewegung vergisst.
 */

/**
 * Gibt unter dem Finger nach wie ein Knopf aus Gummi: Beim Druecken wird das Element
 * etwas kleiner, beim Loslassen federt es zurueck.
 *
 * Bei reduzierter Bewegung bleibt es ruhig - die Ripple-Flaeche zeigt den Druck auch so.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = PRESSED_SCALE,
): Modifier = composed {
    val motion = RezeptliTheme.motion
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reduced) pressedScale else 1f,
        // Hinein knapp, heraus mit Schwung - so fuehlt sich das Loslassen an wie ein Sprung.
        animationSpec = if (pressed) motion.snappy() else motion.bouncy(),
        label = "druck",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Klickbar mit Nachgeben - fuer ganze Karten. Muss *vor* der Kartenflaeche im
 * Modifier stehen, damit die ganze Karte federt und nicht nur ihr Inhalt.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    pressedScale: Float = CARD_PRESSED_SCALE,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    this
        .pressScale(interaction, pressedScale)
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick,
        )
}

/**
 * Merkt sich, welche Eintraege einer Liste schon hereingekommen sind.
 *
 * Eine LazyColumn baut Eintraege neu, wenn sie zurueck ins Bild scrollen. Ohne dieses
 * Gedaechtnis wuerden sie jedes Mal wieder hereinfliegen - nach dem dritten Mal nervt das.
 */
@Stable
class EntranceState internal constructor() {
    internal val shown = mutableSetOf<Any>()
}

@Composable
fun rememberEntranceState(): EntranceState = remember { EntranceState() }

/**
 * Laesst einen Eintrag beim ersten Erscheinen aufsteigen und einfedern - die Eintraege
 * einer Liste nacheinander, nach ihrem [index].
 *
 * Mit [state] und [key] passiert das pro Eintrag nur einmal, auch in einer LazyColumn.
 * Bei reduzierter Bewegung steht alles sofort da.
 */
fun Modifier.entrance(
    index: Int = 0,
    state: EntranceState? = null,
    key: Any? = null,
): Modifier = composed {
    val motion = RezeptliTheme.motion
    val firstTime = remember(key) { state == null || key == null || state.shown.add(key) }
    if (motion.reduced || !firstTime) return@composed this

    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(motion.staggerDelay(index).toLong())
        progress.animateTo(1f, motion.bouncy())
    }
    this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * ENTRANCE_RISE.toPx()
        val scale = ENTRANCE_SCALE + (1f - ENTRANCE_SCALE) * p
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Wackelt kurz, sobald sich [trigger] aendert - etwa wenn etwas im Warenkorb landet.
 * Beim ersten Erscheinen bleibt es ruhig.
 */
fun Modifier.wiggleOnChange(trigger: Any?): Modifier = composed {
    val motion = RezeptliTheme.motion
    val rotation = remember { Animatable(0f) }
    val erstesMal = remember { FirstRun() }
    LaunchedEffect(trigger) {
        if (erstesMal.consume() || motion.reduced) return@LaunchedEffect
        WIGGLE_STEPS.forEach { angle -> rotation.animateTo(angle, motion.snappy()) }
        rotation.animateTo(0f, motion.bouncy())
    }
    graphicsLayer { rotationZ = rotation.value }
}

/**
 * Huepft kurz, sobald sich [trigger] aendert - fuer Zahlen und Symbole, die sich
 * gerade veraendert haben.
 */
fun Modifier.popOnChange(trigger: Any?): Modifier = composed {
    val motion = RezeptliTheme.motion
    val scale = remember { Animatable(1f) }
    val erstesMal = remember { FirstRun() }
    LaunchedEffect(trigger) {
        if (erstesMal.consume() || motion.reduced) return@LaunchedEffect
        scale.animateTo(POP_SCALE, motion.snappy())
        scale.animateTo(1f, motion.bouncy())
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Merkt sich, ob ein Effekt zum ersten Mal laeuft. Bewegungen auf Aenderung sollen beim
 * Erscheinen still bleiben - erst eine echte Aenderung loest sie aus.
 */
internal class FirstRun {
    private var first = true

    /** Liefert beim ersten Aufruf `true`, danach immer `false`. */
    fun consume(): Boolean {
        val war = first
        first = false
        return war
    }
}

private const val PRESSED_SCALE = 0.94f
private const val CARD_PRESSED_SCALE = 0.97f
private val ENTRANCE_RISE = 28.dp
private const val ENTRANCE_SCALE = 0.95f
private const val POP_SCALE = 1.22f
private val WIGGLE_STEPS = listOf(-14f, 11f, -7f, 4f)

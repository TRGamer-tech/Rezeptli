package ch.rezeptli.app.presentation.plan

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.presentation.common.components.CookingPot
import ch.rezeptli.app.presentation.common.components.IconMotion
import ch.rezeptli.app.presentation.common.components.MotionIcon
import ch.rezeptli.app.presentation.common.components.entrance
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.TagColors
import ch.rezeptli.app.presentation.common.theme.Tokens
import kotlinx.coroutines.delay

/*
 * Die ersten Seiten der Einrichtung: begruessen und erklaeren, bevor gefragt wird.
 *
 * Wer eine App zum ersten Mal oeffnet, will wissen, wo er gelandet ist - nicht gleich ein
 * Formular ausfuellen. Deshalb kommt zuerst der Topf aus dem App-Symbol und ein Gruss, dann
 * in fuenf Zeilen, was die App fuer einen tut. Erst danach beginnen die Fragen.
 */

/** Die Begruessung: der dampfende Topf, ein Gruss, ein Satz dazu, worum es geht. */
@Composable
internal fun WelcomePage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .entrance(0)
                .size(216.dp)
                .clip(Tokens.Radius.CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            CookingPot(size = 164.dp, potColor = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Row(
            modifier = Modifier
                .entrance(1)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.willkommen_gruss),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            WavingHand()
        }
        Text(
            text = stringResource(R.string.willkommen_titel),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .entrance(2)
                .semantics { heading() },
        )
        Text(
            text = stringResource(R.string.willkommen_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.entrance(3),
        )
    }
}

/** Die winkende Hand neben dem Gruss: winkt kurz, ruht, winkt wieder. Bei reduzierter Bewegung haelt sie still. */
@Composable
private fun WavingHand(modifier: Modifier = Modifier) {
    val motion = RezeptliTheme.motion
    val winkel = if (motion.reduced) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "winken")
        val wert by transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = WAVE_CYCLE_MS
                    0f at 0
                    0f at WAVE_START_MS
                    20f at WAVE_START_MS + 140
                    -12f at WAVE_START_MS + 280
                    18f at WAVE_START_MS + 420
                    -8f at WAVE_START_MS + 560
                    0f at WAVE_START_MS + 700
                },
            ),
            label = "winkel",
        )
        wert
    }
    Icon(
        imageVector = Icons.Filled.WavingHand,
        contentDescription = null,
        tint = RezeptliTheme.accents.tomato,
        modifier = modifier
            .size(30.dp)
            // Gedreht wird ums Handgelenk, nicht um die Mitte - sonst wackelt die Hand, statt zu winken.
            .graphicsLayer {
                rotationZ = winkel
                transformOrigin = TransformOrigin(pivotFractionX = 0.7f, pivotFractionY = 0.9f)
            },
    )
}

private class Feature(
    val icon: ImageVector,
    val motion: IconMotion,
    val titleRes: Int,
    val textRes: Int,
)

/** Dieselben Symbole und Bewegungen wie in der Leiste unten - so erkennt man sie dort wieder. */
private val FEATURES = listOf(
    Feature(
        Icons.Filled.CalendarMonth,
        IconMotion.FLIP,
        R.string.ueberblick_plan_titel,
        R.string.ueberblick_plan_text,
    ),
    Feature(
        Icons.Filled.ShoppingCart,
        IconMotion.WIGGLE,
        R.string.ueberblick_einkauf_titel,
        R.string.ueberblick_einkauf_text,
    ),
    Feature(Icons.Filled.Style, IconMotion.WIGGLE, R.string.ueberblick_wischen_titel, R.string.ueberblick_wischen_text),
    Feature(
        Icons.AutoMirrored.Filled.MenuBook,
        IconMotion.HOP,
        R.string.ueberblick_sammlung_titel,
        R.string.ueberblick_sammlung_text,
    ),
    Feature(Icons.Filled.Lock, IconMotion.POP, R.string.ueberblick_privat_titel, R.string.ueberblick_privat_text),
)

/**
 * Was die App fuer einen tut - fuenf Zeilen, jede mit ihrem Symbol.
 *
 * Die Zeilen kommen nacheinander herein, und danach bewegen sich die Symbole reihum: eine
 * kleine Welle von oben nach unten, die zeigt, dass hier etwas lebt, ohne zu zappeln.
 */
@Composable
internal fun OverviewPage(modifier: Modifier = Modifier) {
    val farben = RezeptliTheme.tags.all

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(R.string.ueberblick_titel),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .entrance(0)
                .semantics { heading() },
        )
        FEATURES.forEachIndexed { index, feature ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(index + 1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconBadge(
                    icon = feature.icon,
                    motion = feature.motion,
                    colors = farben[index % farben.size],
                    beat = rememberBeat(index = index, of = FEATURES.size),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = stringResource(feature.titleRes), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(feature.textRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.ueberblick_fragen),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .entrance(FEATURES.size + 1)
                .padding(top = 6.dp),
        )
    }
}

/**
 * Ein Zaehler, der reihum weiterspringt: Zeile [index] von [of] ist nach den anderen dran und
 * dann wieder, wenn alle durch sind. Bei reduzierter Bewegung springt er nie.
 */
@Composable
private fun rememberBeat(index: Int, of: Int): Int {
    val motion = RezeptliTheme.motion
    var beat by remember { mutableIntStateOf(0) }
    if (!motion.reduced) {
        LaunchedEffect(index, of) {
            // Erst wenn die Zeile hereingekommen ist - vorher saehe niemand die Bewegung.
            delay(BEAT_START_MS + index * BEAT_STEP_MS)
            while (true) {
                beat += 1
                delay(of * BEAT_STEP_MS + BEAT_PAUSE_MS)
            }
        }
    }
    return beat
}

/**
 * Ein Symbol auf seinem farbigen Fleck. Es bewegt sich beim Erscheinen ([playOnAppear]) und
 * jedes Mal, wenn [beat] weiterspringt.
 */
@Composable
internal fun IconBadge(
    icon: ImageVector,
    motion: IconMotion,
    colors: TagColors,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    beat: Int = 0,
    playOnAppear: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(Tokens.Radius.CircleShape)
            .background(colors.container)
            // Schmuck: Was die Zeile bedeutet, steht im Text daneben.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        MotionIcon(
            imageVector = icon,
            contentDescription = null,
            motion = motion,
            // Der Stand 0 heisst "noch nie": Dann bewegt sich nur, was beim Erscheinen auftreten soll.
            trigger = if (beat > 0) beat else Unit.takeIf { playOnAppear },
            playOnAppear = playOnAppear,
            tint = colors.content,
            modifier = Modifier.size(size * ICON_FRACTION),
        )
    }
}

private const val WAVE_CYCLE_MS = 3200
private const val WAVE_START_MS = 500
private const val BEAT_START_MS = 900L
private const val BEAT_STEP_MS = 450L
private const val BEAT_PAUSE_MS = 2600L
private const val ICON_FRACTION = 0.5f

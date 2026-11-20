package ch.rezeptli.app.presentation.swipe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.presentation.common.components.IconMotion
import ch.rezeptli.app.presentation.common.components.MotionIcon
import ch.rezeptli.app.presentation.common.components.pressScale
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.Tokens

/**
 * Ja und Nein auch als Schaltflaechen - nicht jede und jeder kann oder will wischen,
 * und fuer Screenreader ist das der zuverlaessige Weg.
 *
 * "Nein" ist ein heller Knopf mit rotem Kreuz, "Ja" ein voller gruener mit Herz. Beim
 * Tippen gibt der Knopf nach, und sein Symbol reagiert: Das Kreuz wackelt, das Herz
 * springt auf.
 */
@Composable
fun SwipeDecisionButtons(
    onNo: () -> Unit,
    onYes: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accents = RezeptliTheme.accents

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DecisionButton(
            icon = Icons.Filled.Close,
            label = stringResource(R.string.swipe_no),
            motion = IconMotion.WIGGLE,
            container = accents.cardFill,
            content = accents.swipeNo,
            border = BorderStroke(2.dp, accents.swipeNo.copy(alpha = 0.6f)),
            enabled = enabled,
            onClick = onNo,
        )
        DecisionButton(
            icon = Icons.Filled.Favorite,
            label = stringResource(R.string.swipe_yes),
            motion = IconMotion.POP,
            container = accents.swipeYes,
            content = accents.cardFill,
            border = null,
            enabled = enabled,
            onClick = onYes,
        )
    }
}

@Composable
private fun DecisionButton(
    icon: ImageVector,
    label: String,
    motion: IconMotion,
    container: Color,
    content: Color,
    border: BorderStroke?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    var taps by remember { mutableIntStateOf(0) }
    Surface(
        onClick = {
            taps += 1
            onClick()
        },
        enabled = enabled,
        shape = Tokens.Radius.CircleShape,
        color = container,
        contentColor = content,
        border = border,
        shadowElevation = 6.dp,
        interactionSource = interaction,
        modifier = Modifier
            .size(76.dp)
            .pressScale(interaction, pressedScale = 0.88f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            MotionIcon(
                imageVector = icon,
                contentDescription = label,
                motion = motion,
                trigger = taps,
                tint = content,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

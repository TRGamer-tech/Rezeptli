package ch.rezeptli.app.presentation.common.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.Tokens

/**
 * Leerzustand, der aktiv weiterhilft statt nur eine leere Flaeche zu zeigen.
 *
 * Das Symbol schwebt auf einem weichen Fleck und springt beim Erscheinen auf - ein
 * leerer Bildschirm soll einladen, nicht tot wirken. Es ist rein dekorativ und wird
 * vor Screenreadern verborgen; der Text daneben sagt bereits alles.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        FloatingBadge(icon = icon, modifier = Modifier.entrance(0))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.entrance(1),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.entrance(2),
        )
        if (primaryActionLabel != null && onPrimaryAction != null) {
            BouncyButton(
                onClick = onPrimaryAction,
                modifier = Modifier
                    .entrance(3)
                    .padding(top = 8.dp)
                    .heightIn(min = 52.dp),
            ) {
                Text(primaryActionLabel)
            }
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            BouncyTextButton(
                onClick = onSecondaryAction,
                modifier = Modifier.entrance(4),
            ) {
                Text(secondaryActionLabel)
            }
        }
    }
}

/** Das Symbol auf seinem Fleck - schwebt sanft auf und ab, solange Bewegung erlaubt ist. */
@Composable
private fun FloatingBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    val motion = RezeptliTheme.motion
    val bob = if (motion.reduced) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "schweben")
        val value by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(FLOAT_MS, easing = Tokens.Ease.Standard),
                RepeatMode.Reverse,
            ),
            label = "auf-ab",
        )
        value
    }

    Box(
        modifier = modifier
            .size(112.dp)
            .graphicsLayer { translationY = bob * FLOAT_HEIGHT.toPx() }
            .clip(Tokens.Radius.CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        MotionIcon(
            imageVector = icon,
            contentDescription = null,
            motion = IconMotion.POP,
            playOnAppear = true,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(52.dp),
        )
    }
}

private const val FLOAT_MS = 1700
private val FLOAT_HEIGHT = 5.dp

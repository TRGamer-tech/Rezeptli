package ch.rezeptli.app.presentation.common.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.Tokens

/**
 * Ein Fortschrittsbalken, der zu seinem neuen Stand gleitet statt zu springen - wie ein
 * Glas, das sich fuellt.
 *
 * Fuer Screenreader ist er ein echter Fortschrittsbalken mit Wert.
 */
@Composable
fun RezeptliProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 10.dp,
) {
    val motion = RezeptliTheme.motion
    val wert = progress.coerceIn(0f, 1f)
    val angezeigt by animateFloatAsState(
        targetValue = wert,
        animationSpec = motion.progress(),
        label = "fortschritt",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(Tokens.Radius.PillShape)
            .background(trackColor)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(wert, 0f..1f) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(angezeigt)
                .clip(Tokens.Radius.PillShape)
                .background(color),
        )
    }
}

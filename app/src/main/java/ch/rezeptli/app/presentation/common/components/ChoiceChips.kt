package ch.rezeptli.app.presentation.common.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.Tokens

/**
 * Eine Reihe auswaehlbarer Schlagworte, die bei Bedarf umbricht.
 *
 * Die Chips sind mindestens 48 dp hoch - das ist die kleinste Flaeche, die sich
 * zuverlaessig mit dem Finger treffen laesst. Fuer Screenreader sind sie als
 * Auswahlkaestchen ausgezeichnet, nicht als Knopf: Sie schalten etwas ein und aus.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChipGroup(
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            RezeptliFilterChip(
                selected = option in selected,
                onClick = { onToggle(option) },
                label = label(option),
            )
        }
    }
}

/**
 * Ein Auswahl-Chip der App: gibt unter dem Finger nach und zeigt die Auswahl mit Farbe,
 * Rand und einem kleinen Haken an der Ecke.
 *
 * Der Chip bleibt dabei gleich breit. Frueher schob ein Haken vor dem Text den Chip
 * breiter - in einer umbrechenden Reihe rutschten dann alle folgenden Chips auf eine
 * andere Zeile, und man tippte beim zweiten Mal daneben. Der Haken sitzt jetzt auf der
 * Ecke, ausserhalb des Textflusses.
 */
@Composable
fun RezeptliFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val motion = RezeptliTheme.motion
    Box(modifier = modifier.pressScale(interaction)) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) },
            enabled = enabled,
            shape = Tokens.Radius.PillShape,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { role = Role.Checkbox },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = enabled,
                selected = selected,
                selectedBorderColor = MaterialTheme.colorScheme.primary,
                selectedBorderWidth = 2.dp,
            ),
            interactionSource = interaction,
        )
        AnimatedVisibility(
            visible = selected,
            enter = if (motion.reduced) fadeIn(motion.fade()) else scaleIn(motion.bouncy()) + fadeIn(motion.fade()),
            exit = if (motion.reduced) fadeOut(motion.fade()) else scaleOut(motion.snappy()) + fadeOut(motion.fade()),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = 2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

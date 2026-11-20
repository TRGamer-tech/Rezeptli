package ch.rezeptli.app.presentation.common.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
 * Ein Auswahl-Chip der App: gibt unter dem Finger nach, und der Haken springt beim
 * Auswaehlen auf.
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
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        enabled = enabled,
        shape = Tokens.Radius.PillShape,
        modifier = modifier
            .pressScale(interaction)
            .heightIn(min = 48.dp)
            .semantics { role = Role.Checkbox },
        leadingIcon = if (selected) {
            {
                MotionIcon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    motion = IconMotion.POP,
                    playOnAppear = true,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(),
        interactionSource = interaction,
    )
}

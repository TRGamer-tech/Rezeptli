package ch.rezeptli.app.presentation.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.TagShortlist

/**
 * Die Schlagworte eines Filters - zuerst nur die meistbenutzten, der Rest auf Wunsch.
 *
 * [tags] kommen nach Haeufigkeit geordnet, die meistbenutzten zuerst. Was eingeklappt zu
 * sehen ist, entscheidet [TagShortlist]; angewaehlte Schlagworte bleiben immer stehen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagFilterChips(
    tags: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shortlist = TagShortlist.of(byUse = tags, selected = selected, expanded = expanded)

    Column(modifier = modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shortlist.visible.forEach { tag ->
                RezeptliFilterChip(
                    selected = tag in selected,
                    onClick = { onToggle(tag) },
                    label = tag,
                )
            }
        }

        if (shortlist.canToggle) {
            BouncyTextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = if (expanded) {
                        stringResource(R.string.tags_weniger_anzeigen)
                    } else {
                        stringResource(R.string.tags_alle_anzeigen, shortlist.total)
                    },
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

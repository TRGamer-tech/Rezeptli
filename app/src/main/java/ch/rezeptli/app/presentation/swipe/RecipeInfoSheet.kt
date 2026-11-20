package ch.rezeptli.app.presentation.swipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.presentation.common.components.BouncyOutlinedButton
import ch.rezeptli.app.presentation.common.components.CookingPot
import ch.rezeptli.app.presentation.common.components.NutritionNote
import ch.rezeptli.app.presentation.common.components.PrepTimeLabel
import ch.rezeptli.app.presentation.common.displayText

/**
 * Das Blatt, das beim Antippen einer Wischkarte aufgeht: Quelle, Zeit, Zutaten.
 *
 * Bewusst ohne Zubereitung - siehe [CardInfo].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeInfoSheet(
    info: CardInfo,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = info.title, style = MaterialTheme.typography.headlineSmall)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                info.sourceName?.let { quelle ->
                    Text(
                        text = stringResource(R.string.karte_quelle, quelle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                info.minutes?.let { PrepTimeLabel(it) }
                info.servings?.takeIf { it.isNotBlank() }?.let { portionen ->
                    Text(
                        // Quellen schreiben mal "4", mal "4 Portionen", mal "1 Blech" - nur die
                        // nackte Zahl braucht ein Wort dazu.
                        text = if (portionen.all(Char::isDigit)) {
                            stringResource(R.string.karte_portionen, portionen)
                        } else {
                            portionen
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            info.sourceUrl?.let { adresse ->
                BouncyOutlinedButton(
                    // Ohne Browser auf dem Geraet darf der Tipp nichts kaputtmachen.
                    onClick = { runCatching { uriHandler.openUri(adresse) } },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(stringResource(R.string.rezept_quelle_oeffnen), modifier = Modifier.padding(start = 8.dp))
                }
            }

            Text(text = stringResource(R.string.recipe_ingredients), style = MaterialTheme.typography.titleLarge)

            when {
                info.isLoading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CookingPot(size = 40.dp)
                    Text(
                        text = stringResource(R.string.karte_zutaten_laden),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                info.isPartnersOwn -> Note(R.string.karte_eigenes_rezept)

                info.failed -> Note(R.string.karte_zutaten_fehler)

                !info.hasIngredients -> Note(R.string.recipe_no_ingredients)

                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    info.lines.forEach { zeile -> IngredientLine(zeile) }
                    info.ingredients.forEach { zutat -> IngredientLine(zutat.displayText()) }
                    if (info.isForeign) Note(R.string.karte_fremdsprachig)
                }
            }

            // Unter den Zutaten: was eine Portion an Kalorien und Eiweiss bringt.
            if (info.showsNutrition) {
                NutritionNote(facts = info.nutrition, sourceName = info.sourceName)
            }
        }
    }
}

@Composable
private fun IngredientLine(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Note(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

package ch.rezeptli.app.presentation.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.plan.IngredientScaler
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Die Rueckfragen rund um ein Rezept - von der Liste und von der Detailseite gleich gestellt.
 */

/** Ein Datum, wie man es hier schreibt: "12.11.2026". */
fun formatCookedDate(millis: Long): String =
    DATE.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.yyyy")

/** Fragt nach, bevor ein Rezept aus der Sammlung verschwindet. */
@Composable
fun RemoveRecipeDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rezept_entfernen_titel)) },
        text = { Text(stringResource(R.string.rezept_entfernen_text, title)) },
        confirmButton = {
            BouncyTextButton(onClick = onConfirm) {
                Text(stringResource(R.string.rezept_entfernen), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            BouncyTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Fragt nach, bevor ein Rezept als gekocht vermerkt wird.
 *
 * War es schon einmal gekocht, steht das Datum dabei - und der Vermerk laesst sich auch
 * wieder entfernen, falls er ein Versehen war.
 */
@Composable
fun CookedDialog(
    title: String,
    lastCookedAt: Long?,
    onConfirm: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rezept_gekocht_titel)) },
        text = {
            Text(
                if (lastCookedAt == null) {
                    stringResource(R.string.rezept_gekocht_text, title)
                } else {
                    stringResource(R.string.rezept_gekocht_text_erneut, title, formatCookedDate(lastCookedAt))
                },
            )
        },
        confirmButton = {
            BouncyTextButton(onClick = onConfirm) { Text(stringResource(R.string.rezept_gekocht_bestaetigen)) }
        },
        dismissButton = {
            Row {
                if (lastCookedAt != null) {
                    BouncyTextButton(onClick = onClear) { Text(stringResource(R.string.rezept_gekocht_entfernen)) }
                }
                BouncyTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/**
 * Fragt, fuer wie viele eingekauft wird, bevor Zutaten auf die Einkaufsliste kommen.
 *
 * [recipeServings] sagt, fuer wie viele das Rezept gedacht ist. Nennt es das nicht, fragt
 * der Dialog auch danach: Ohne diese Zahl laesst sich nichts umrechnen, und eine stille
 * Annahme waere geraten. Bei [several] Rezepten auf einmal gilt eine Personenzahl fuer alle.
 *
 * [onConfirm] bekommt die Personenzahl und - nur wenn danach gefragt wurde - fuer wie viele
 * das Rezept reicht.
 */
@Composable
fun PortionDialog(
    defaultPersons: Int,
    recipeServings: Int?,
    onConfirm: (persons: Int, baseServings: Int?) -> Unit,
    onDismiss: () -> Unit,
    several: Boolean = false,
) {
    var persons by rememberSaveable { mutableIntStateOf(defaultPersons.coerceIn(1, UserProfile.MAX_HOUSEHOLD_SIZE)) }
    var base by rememberSaveable { mutableIntStateOf(recipeServings ?: IngredientScaler.DEFAULT_SERVINGS) }
    val fragtNachBasis = !several && recipeServings == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.portionen_titel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AmountStepper(
                    value = persons,
                    caption = pluralStringResource(R.plurals.portionen_personen, persons),
                    range = 1..UserProfile.MAX_HOUSEHOLD_SIZE,
                    onChange = { persons = it },
                )
                when {
                    several -> Text(
                        text = stringResource(R.string.portionen_mehrere, IngredientScaler.DEFAULT_SERVINGS),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    fragtNachBasis -> {
                        Text(
                            text = stringResource(R.string.portionen_unbekannt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AmountStepper(
                            value = base,
                            caption = stringResource(R.string.portionen_im_rezept),
                            range = 1..MAX_BASE_SERVINGS,
                            onChange = { base = it },
                        )
                    }

                    else -> Text(
                        text = if (persons == base) {
                            stringResource(R.string.portionen_passt, base)
                        } else {
                            stringResource(R.string.portionen_umrechnen, base)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            BouncyTextButton(onClick = { onConfirm(persons, base.takeIf { fragtNachBasis }) }) {
                Text(stringResource(R.string.portionen_bestaetigen))
            }
        },
        dismissButton = {
            BouncyTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Minus, Zahl mit Beschriftung, Plus - gross genug fuer den Daumen, klein genug fuer einen Dialog. */
@Composable
fun AmountStepper(
    value: Int,
    caption: String,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BouncyFilledTonalIconButton(
            onClick = { onChange(value - 1) },
            enabled = value > range.first,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.portionen_weniger))
        }
        Column(
            modifier = Modifier
                .widthIn(min = 120.dp)
                .semantics(mergeDescendants = true) { contentDescription = "$value $caption" },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString(),
                style = RezeptliTheme.amountStyle.merge(MaterialTheme.typography.headlineMedium),
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        BouncyFilledTonalIconButton(
            onClick = { onChange(value + 1) },
            enabled = value < range.last,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.portionen_mehr))
        }
    }
}

/** Mehr als das ist keine Portionsangabe mehr - siehe ServingsParser. */
private const val MAX_BASE_SERVINGS = 24

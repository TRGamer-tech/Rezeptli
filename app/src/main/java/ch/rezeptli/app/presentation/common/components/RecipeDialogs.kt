package ch.rezeptli.app.presentation.common.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ch.rezeptli.app.R
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

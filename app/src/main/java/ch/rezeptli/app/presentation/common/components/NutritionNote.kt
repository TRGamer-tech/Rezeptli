package ch.rezeptli.app.presentation.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.nutrition.NutritionBasis
import ch.rezeptli.app.domain.nutrition.NutritionFacts
import ch.rezeptli.app.domain.nutrition.NutritionValue

/**
 * Kalorien und Eiweiss eines Rezepts - der Block unter der Zutatenliste.
 *
 * Er sagt immer dazu, woher die Zahlen stammen: von der Rezeptseite oder aus den Zutaten
 * geschaetzt. Eine Schaetzung traegt "ca." und ist auf Zehner gerundet; sie soll nicht
 * genauer aussehen, als sie ist. Liess sich nichts schaetzen, steht auch das da - eine
 * fehlende Zahl ist ehrlicher als eine erfundene.
 */
@Composable
fun NutritionNote(
    facts: NutritionFacts?,
    modifier: Modifier = Modifier,
    /** Der Name der Quelle, falls bekannt - "Angaben von Betty Bossi" sagt mehr als "der Quelle". */
    sourceName: String? = null,
) {
    Column(
        // Als ein Stueck vorgelesen: Ueberschrift, beide Werte und woher sie kommen.
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(
                if (facts?.basis == NutritionBasis.RECIPE) {
                    R.string.naehrwerte_ganzes_rezept
                } else {
                    R.string.naehrwerte_pro_portion
                },
            ),
            style = MaterialTheme.typography.titleSmall,
        )

        if (facts == null) {
            Text(
                text = stringResource(R.string.naehrwerte_unbekannt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            facts.kcal?.let { kcal ->
                NutritionFigure(
                    value = stringResource(
                        if (kcal.estimated) R.string.naehrwerte_kcal_ca else R.string.naehrwerte_kcal,
                        kcal.amount,
                    ),
                    label = stringResource(R.string.naehrwerte_kalorien),
                )
            }
            facts.protein?.let { protein ->
                NutritionFigure(
                    value = stringResource(
                        if (protein.estimated) R.string.naehrwerte_gramm_ca else R.string.naehrwerte_gramm,
                        protein.amount,
                    ),
                    label = stringResource(R.string.naehrwerte_eiweiss),
                )
            }
        }

        Text(
            text = facts.originText(sourceName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NutritionFigure(value: String, label: String) {
    Column {
        Text(text = value, style = MaterialTheme.typography.titleLarge)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Woher die Zahlen stammen - in einem Satz. */
@Composable
private fun NutritionFacts.originText(sourceName: String?): String = when {
    isFromSource -> if (sourceName.isNullOrBlank()) {
        stringResource(R.string.naehrwerte_quelle)
    } else {
        stringResource(R.string.naehrwerte_quelle_name, sourceName)
    }
    isEstimate -> stringResource(
        if (basis == NutritionBasis.RECIPE) R.string.naehrwerte_geschaetzt_ganz else R.string.naehrwerte_geschaetzt,
    )
    kcal.isStated() -> stringResource(R.string.naehrwerte_kalorien_quelle)
    else -> stringResource(R.string.naehrwerte_eiweiss_quelle)
}

private fun NutritionValue?.isStated(): Boolean = this != null && !estimated

package ch.rezeptli.app.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.presentation.common.theme.Tokens
import ch.rezeptli.app.presentation.common.theme.cardSurface
import coil.compose.AsyncImage
import kotlin.math.abs

/**
 * Eintrag in der Rezeptliste: kleines Bild, Titel, Zeit und Tags in einer Zeile.
 *
 * Bewusst kompakt statt gross: Diese Liste sieht man am haeufigsten, und bei einer
 * Sammlung von achtzig Rezepten zaehlt die Uebersicht mehr als die Bildgroesse. Die
 * grossen Bilder haben ihren Platz im Wisch-Modus.
 */
@Composable
fun RecipeListCard(
    recipe: RecipeSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(Tokens.Radius.LgShape)
            .clickable(onClick = onClick)
            // 48 dp waere die Untergrenze fuer eine Tippflaeche; hier sind es mehr,
            // weil das Bild ohnehin quadratisch ist.
            .heightIn(min = 88.dp)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeImage(
            photoUri = recipe.photoUri,
            title = recipe.title,
            modifier = Modifier
                .size(72.dp)
                .clip(Tokens.Radius.MdShape),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                recipe.prepTimeMinutes?.let { minutes -> PrepTimeLabel(minutes) }
                if (recipe.tags.isNotEmpty()) {
                    Text(
                        text = recipe.tags.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Bild eines Rezepts, mit einer freundlichen Platzhalter-Flaeche, wenn keines da ist. */
@Composable
fun RecipeImage(
    photoUri: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    if (photoUri.isNullOrBlank()) {
        // Statt einer grauen Flaeche ein Farbverlauf aus der Mesh-Palette des Kits.
        // Er haengt am Titel, ist also fuer dasselbe Rezept immer derselbe - eine
        // Karte, die bei jedem Blaettern die Farbe wechselt, waere nur unruhig.
        val mesh = Tokens.Mesh.All[abs(title.hashCode()) % Tokens.Mesh.All.size]
        Box(
            modifier = modifier.background(
                Brush.linearGradient(
                    listOf(
                        mesh.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Restaurant,
                contentDescription = stringResource(R.string.recipe_photo_placeholder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
        }
    } else {
        AsyncImage(
            model = photoUri,
            contentDescription = stringResource(R.string.recipe_photo_description, title),
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

/** Zubereitungszeit mit Uhr-Symbol. */
@Composable
fun PrepTimeLabel(
    minutes: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.recipe_prep_time, minutes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

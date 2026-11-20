package ch.rezeptli.app.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.TagColors
import ch.rezeptli.app.presentation.common.theme.Tokens
import ch.rezeptli.app.presentation.common.theme.cardSurface
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
            .bouncyClickable(onClick = onClick)
            .cardSurface(Tokens.Radius.LgShape)
            // 48 dp waere die Untergrenze fuer eine Tippflaeche; hier sind es mehr,
            // weil das Bild ohnehin quadratisch ist.
            .heightIn(min = 92.dp)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
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

/**
 * Bild eines Rezepts, mit einer freundlichen Platzhalter-Flaeche, wenn keines da ist.
 *
 * Das Foto blendet weich ein, sobald es geladen ist. Kommt es aus dem Speicher - etwa
 * wenn eine Wischkarte nach vorne rueckt -, steht es sofort da; Coil ueberspringt die
 * Blende dann von selbst.
 */
@Composable
fun RecipeImage(
    photoUri: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    // Ein Bild kann auch dann fehlen, wenn eine Adresse da ist: Die Seite kann sie
    // zurueckziehen, das Netz kann weg sein, der Anbieter kann fremdes Einbinden
    // sperren. Dann soll dieselbe Flaeche erscheinen wie ohne Foto - eine Luecke oder
    // ein kaputtes Bildsymbol waere schlechter als eine ruhige Farbe.
    var fehlgeschlagen by remember(photoUri) { mutableStateOf(false) }

    if (photoUri.isNullOrBlank() || fehlgeschlagen) {
        RecipePhotoPlaceholder(title = title, modifier = modifier)
    } else {
        val context = LocalContext.current
        val reduced = RezeptliTheme.motion.reduced
        val request = remember(photoUri, reduced) {
            ImageRequest
                .Builder(context)
                .data(photoUri)
                .crossfade(if (reduced) Tokens.Duration.Instant else Tokens.Duration.Base)
                .build()
        }
        // Waehrend das Foto laedt, steht die Platzhalterfarbe da - kein weisses Loch.
        Box(modifier = modifier.background(placeholderBrush(title))) {
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.recipe_photo_description, title),
                contentScale = ContentScale.Crop,
                onError = { fehlgeschlagen = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Ein Verlauf in einer Marktfarbe - die Farbe haengt am Titel. */
@Composable
private fun placeholderBrush(title: String): Brush {
    val farbe = placeholderColors(title)
    return Brush.linearGradient(listOf(farbe.container, MaterialTheme.colorScheme.surfaceContainerLow))
}

@Composable
private fun placeholderColors(title: String): TagColors {
    val farben = RezeptliTheme.tags.all
    return farben[abs(title.hashCode()) % farben.size]
}

/**
 * Die Flaeche fuer ein Rezept ohne brauchbares Foto: ein Verlauf in einer Marktfarbe
 * mit dem Kochtopf darauf.
 *
 * Die Farbe haengt am Titel, ist also fuer dasselbe Rezept immer dieselbe - eine Karte,
 * die bei jedem Blaettern die Farbe wechselt, waere nur unruhig.
 */
@Composable
private fun RecipePhotoPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
) {
    val farbe = placeholderColors(title)
    val beschreibung = stringResource(R.string.recipe_photo_placeholder)
    Box(
        modifier = modifier
            .background(placeholderBrush(title))
            .semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center,
    ) {
        CookingPot(
            animated = false,
            size = 48.dp,
            potColor = farbe.content,
            steamColor = farbe.content.copy(alpha = 0.55f),
            heartColor = farbe.container,
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

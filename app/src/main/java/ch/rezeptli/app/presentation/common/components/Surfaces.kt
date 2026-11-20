package ch.rezeptli.app.presentation.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.presentation.common.theme.Tokens
import ch.rezeptli.app.presentation.common.theme.cardSurface

/*
 * Die Bausteine, aus denen die Bildschirme bestehen.
 *
 * Farben und Schrift kommen aus dem Thema und gelten ueberall von selbst. Was sich
 * nicht von selbst ergibt, ist die Flaechensprache: Karten wie Papier auf Creme, Titel
 * in der Kochbuchschrift, gleiche Abstaende. Hier steht sie einmal.
 */

/** Die Kopfzeile aller Bildschirme: gleiche Farbe, gleiche Hoehe, gleicher Rueckweg. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RezeptliTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    titleStyle: TextStyle? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = titleStyle ?: MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            when {
                navigationIcon != null -> navigationIcon()
                onBack != null ->
                    BouncyIconButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

/**
 * Ein abgesetzter Abschnitt - die Grundform fuer alles, was zusammengehoert.
 *
 * Ohne [title] ist es einfach eine Karte; mit Titel eine benannte Gruppe, der Titel in
 * der Kochbuchschrift. Beim ersten Erscheinen steigt die Karte leicht auf - mit [index]
 * kommen mehrere Abschnitte nacheinander.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    spacing: Int = 12,
    index: Int = 0,
    entranceState: EntranceState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            // In einer LazyColumn merkt sich [entranceState], dass die Karte schon da war -
            // sonst kaeme sie bei jedem Zurueckscrollen erneut hereingeflogen.
            .entrance(index, entranceState, title ?: index)
            .fillMaxWidth()
            .cardSurface(Tokens.Radius.LgShape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        content()
    }
}

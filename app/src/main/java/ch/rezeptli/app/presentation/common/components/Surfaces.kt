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
import androidx.compose.material3.IconButton
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
 * nicht von selbst ergibt, ist die Flaechensprache des Kits: gerundete Karten mit
 * Haarlinie, eine ruhige Kopfzeile, gleiche Abstaende. Frueher stand das auf jedem
 * Bildschirm einzeln - oder eben gar nicht. Hier steht es einmal.
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
                style = titleStyle ?: MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            when {
                navigationIcon != null -> navigationIcon()
                onBack != null ->
                    IconButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
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
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/**
 * Ein abgesetzter Abschnitt - die Grundform fuer alles, was zusammengehoert.
 *
 * Ohne [title] ist es einfach eine Karte; mit Titel eine benannte Gruppe. Der Rahmen
 * ist eine Haarlinie, keine Schattenkante: Das Kit setzt Tiefe ueber Kontrast, nicht
 * ueber Schlagschatten.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    spacing: Int = 12,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(Tokens.Radius.LgShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        content()
    }
}

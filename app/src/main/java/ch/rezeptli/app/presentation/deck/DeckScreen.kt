package ch.rezeptli.app.presentation.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.presentation.common.components.EmptyState
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.components.SectionCard
import ch.rezeptli.app.presentation.common.theme.Tokens
import ch.rezeptli.app.presentation.common.theme.cardSurface
import ch.rezeptli.app.presentation.swipe.SwipeCardStack

/**
 * Der Einstieg in die App: wischen, nicht suchen.
 *
 * Der Stapel kommt aus dem Rezeptverzeichnis und richtet sich nach dem, was im
 * Onboarding angegeben wurde. Vor jeder Runde steht die Frage, wie viele Gerichte es
 * werden sollen - danach zaehlt die Runde mit und hoert von selbst auf.
 */
@Composable
fun DeckRoute(
    onOpenTogether: () -> Unit,
    onSaved: (Int) -> Unit,
    viewModel: DeckViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DeckScreen(
        uiState = uiState,
        onTargetChange = viewModel::onTargetChange,
        onStart = viewModel::onStart,
        onSwiped = viewModel::onSwiped,
        onFinishEarly = viewModel::onFinishEarly,
        onRestart = viewModel::onRestart,
        onKeep = { viewModel.onKeepLiked(onSaved) },
        onTogether = {
            viewModel.prepareTogether()
            onOpenTogether()
        },
    )
}

@Composable
fun DeckScreen(
    uiState: DeckUiState,
    onTargetChange: (Int?) -> Unit,
    onStart: () -> Unit,
    onSwiped: (DeckEntry, Boolean) -> Unit,
    onFinishEarly: () -> Unit,
    onRestart: () -> Unit,
    onKeep: () -> Unit,
    onTogether: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            RezeptliTopBar(
                title = stringResource(R.string.stapel_titel),
                actions = {
                    if (uiState.step == DeckStep.WISCHEN && uiState.target != null) {
                        TextButton(onClick = onFinishEarly) {
                            Text(stringResource(R.string.stapel_genug))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (uiState.step) {
                DeckStep.ANZAHL -> TargetStep(
                    uiState = uiState,
                    onTargetChange = onTargetChange,
                    onStart = onStart,
                )

                DeckStep.LADEN -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                DeckStep.WISCHEN -> SwipingStep(
                    uiState = uiState,
                    onSwiped = onSwiped,
                    onFinishEarly = onFinishEarly,
                )

                DeckStep.FERTIG -> ResultStep(
                    uiState = uiState,
                    onKeep = onKeep,
                    onTogether = onTogether,
                    onRestart = onRestart,
                )
            }
        }
    }
}

/** Die Frage vor jeder Runde: wie viele Gerichte? */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetStep(
    uiState: DeckUiState,
    onTargetChange: (Int?) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = stringResource(R.string.stapel_anzahl_frage)) {
            Text(
                stringResource(R.string.stapel_anzahl_hinweis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TARGET_OPTIONS.forEach { anzahl ->
                    FilterChip(
                        selected = uiState.target == anzahl,
                        onClick = { onTargetChange(anzahl) },
                        label = {
                            Text(pluralStringResource(R.plurals.stapel_gerichte, anzahl, anzahl))
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                FilterChip(
                    selected = uiState.target == null,
                    onClick = { onTargetChange(null) },
                    label = { Text(stringResource(R.string.stapel_endlos)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        if (uiState.loadFailed) {
            Text(
                stringResource(R.string.stapel_kein_vorrat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.stapel_losgehts))
        }
    }
}

@Composable
private fun SwipingStep(
    uiState: DeckUiState,
    onSwiped: (DeckEntry, Boolean) -> Unit,
    onFinishEarly: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DeckProgress(uiState = uiState)

        if (uiState.isEmpty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            SwipeCardStack(
                cards = uiState.remainingCards,
                onSwiped = { summary, liked ->
                    // Der Stapel kennt nur RecipeSummary; die Kennung ist die Adresse.
                    val eintrag = uiState.cards.firstOrNull { it.url.hashCode().toLong() == summary.id }
                    if (eintrag != null) onSwiped(eintrag, liked)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }

        // Ohne Ziel gibt es keine Zahl, bei der die Runde von selbst endet - dafuer
        // dieser eigene, gut sichtbare Knopf statt der kleinen Textschaltflaeche oben.
        if (uiState.target == null) {
            Button(
                onClick = onFinishEarly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.stapel_fertig))
            }
        }
    }
}

/** Zeigt leise mit, wie weit die Runde ist - ohne den Stapel zu verdecken. */
@Composable
private fun DeckProgress(uiState: DeckUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val ziel = uiState.target
        if (ziel != null) {
            Text(
                text = stringResource(R.string.stapel_fortschritt, uiState.foundCount, ziel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = stringResource(R.string.stapel_fortschritt_endlos, uiState.foundCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Was die Runde ergeben hat, und was sich damit anfangen laesst. */
@Composable
private fun ResultStep(
    uiState: DeckUiState,
    onKeep: () -> Unit,
    onTogether: () -> Unit,
    onRestart: () -> Unit,
) {
    if (uiState.liked.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Restaurant,
            title = stringResource(R.string.stapel_nichts_titel),
            message = stringResource(R.string.stapel_nichts_text),
            primaryActionLabel = stringResource(R.string.stapel_neue_runde),
            onPrimaryAction = onRestart,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "kopf") {
            Text(
                text = pluralStringResource(
                    R.plurals.stapel_ergebnis_titel,
                    uiState.liked.size,
                    uiState.liked.size,
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        items(uiState.liked, key = { it.url }) { eintrag ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .cardSurface(Tokens.Radius.MdShape)
                    .padding(12.dp),
            ) {
                Text(eintrag.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    eintrag.sourceName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "aktionen") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onKeep,
                    enabled = !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        if (uiState.isSaving) {
                            stringResource(
                                R.string.stapel_speichern_laeuft,
                                uiState.savedCount,
                                uiState.liked.size,
                            )
                        } else {
                            stringResource(R.string.stapel_speichern)
                        },
                    )
                }

                // Party-Modus: dieselbe Auswahl, aber zu zweit entschieden.
                OutlinedButton(
                    onClick = onTogether,
                    enabled = !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Icon(imageVector = Icons.Filled.Group, contentDescription = null)
                    Text(
                        text = stringResource(R.string.stapel_zu_zweit),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                TextButton(
                    onClick = onRestart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.stapel_neue_runde),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

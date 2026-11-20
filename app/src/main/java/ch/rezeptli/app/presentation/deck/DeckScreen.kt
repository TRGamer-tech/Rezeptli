package ch.rezeptli.app.presentation.deck

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.presentation.common.components.BouncyButton
import ch.rezeptli.app.presentation.common.components.BouncyOutlinedButton
import ch.rezeptli.app.presentation.common.components.BouncyTextButton
import ch.rezeptli.app.presentation.common.components.ConfettiBurst
import ch.rezeptli.app.presentation.common.components.EmptyState
import ch.rezeptli.app.presentation.common.components.IconMotion
import ch.rezeptli.app.presentation.common.components.MotionIcon
import ch.rezeptli.app.presentation.common.components.RecipeImage
import ch.rezeptli.app.presentation.common.components.RezeptliFilterChip
import ch.rezeptli.app.presentation.common.components.RezeptliLoader
import ch.rezeptli.app.presentation.common.components.RezeptliProgressBar
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.components.SectionCard
import ch.rezeptli.app.presentation.common.components.entrance
import ch.rezeptli.app.presentation.common.components.popOnChange
import ch.rezeptli.app.presentation.common.components.rememberEntranceState
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.Tokens
import ch.rezeptli.app.presentation.common.theme.cardSurface
import ch.rezeptli.app.presentation.swipe.SwipeCardStack
import ch.rezeptli.app.presentation.swipe.SwipeDecisionButtons
import ch.rezeptli.app.presentation.swipe.rememberSwipeCardStackState

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
    onLeave: () -> Unit = {},
    viewModel: DeckViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var abbrechenFragen by remember { mutableStateOf(false) }

    // Mitten im Wischen fuehrt "Zurueck" nicht einfach hinaus: Ein versehentlicher Wisch
    // vom Bildschirmrand haette sonst die ganze Runde samt Auswahl gekostet.
    val etwasZuVerlieren = uiState.step == DeckStep.WISCHEN ||
        (uiState.step == DeckStep.FERTIG && uiState.liked.isNotEmpty())
    BackHandler(enabled = etwasZuVerlieren) { abbrechenFragen = true }

    if (abbrechenFragen) {
        AlertDialog(
            onDismissRequest = { abbrechenFragen = false },
            title = { Text(stringResource(R.string.stapel_verlassen_titel)) },
            text = { Text(stringResource(R.string.stapel_verlassen_text)) },
            confirmButton = {
                BouncyTextButton(onClick = { abbrechenFragen = false }) {
                    Text(stringResource(R.string.stapel_weiterwischen))
                }
            },
            dismissButton = {
                BouncyTextButton(onClick = {
                    abbrechenFragen = false
                    viewModel.onRestart()
                    onLeave()
                }) {
                    Text(stringResource(R.string.stapel_verlassen), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    DeckScreen(
        uiState = uiState,
        onTargetChange = viewModel::onTargetChange,
        onStart = viewModel::onStart,
        onSwiped = viewModel::onSwiped,
        onFinishEarly = viewModel::onFinishEarly,
        onRestart = viewModel::onRestart,
        // Zurueck auf den Anfang der naechsten Runde statt weg von der Karte -
        // sonst landet man nach dem Speichern bei den Rezepten und muesste den
        // Umweg ueber die Leiste nehmen, um weiterzuwischen.
        onKeep = { viewModel.onKeepLiked { viewModel.onRestart() } },
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
    val motion = RezeptliTheme.motion
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            RezeptliTopBar(
                title = stringResource(R.string.stapel_titel),
                actions = {
                    if (uiState.step == DeckStep.WISCHEN && uiState.target != null) {
                        BouncyTextButton(onClick = onFinishEarly) {
                            Text(stringResource(R.string.stapel_genug))
                        }
                    }
                },
            )
        },
    ) { padding ->
        // Jeder Schritt der Runde kommt mit einer kleinen Bewegung herein - der Wechsel
        // von der Frage zum Stapel zur Ausbeute soll sich wie ein Weitergehen anfuehlen.
        AnimatedContent(
            targetState = uiState.step,
            transitionSpec = { motion.enter() togetherWith motion.exit() },
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "runde",
        ) { step ->
            when (step) {
                DeckStep.ANZAHL -> TargetStep(
                    uiState = uiState,
                    onTargetChange = onTargetChange,
                    onStart = onStart,
                )

                DeckStep.LADEN -> RezeptliLoader(message = stringResource(R.string.laden_stapel))

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
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SectionCard(title = stringResource(R.string.stapel_anzahl_frage), index = 0) {
            Text(
                stringResource(R.string.stapel_anzahl_hinweis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TARGET_OPTIONS.forEach { anzahl ->
                    RezeptliFilterChip(
                        selected = uiState.target == anzahl,
                        onClick = { onTargetChange(anzahl) },
                        label = pluralStringResource(R.plurals.stapel_gerichte, anzahl, anzahl),
                    )
                }
                RezeptliFilterChip(
                    selected = uiState.target == null,
                    onClick = { onTargetChange(null) },
                    label = stringResource(R.string.stapel_endlos),
                )
            }
        }

        if (uiState.loadFailed) {
            Text(
                stringResource(R.string.stapel_kein_vorrat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.entrance(1),
            )
        }

        BouncyButton(
            onClick = onStart,
            modifier = Modifier
                .entrance(2)
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.stapel_losgehts), style = MaterialTheme.typography.titleMedium)
            MotionIcon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                motion = IconMotion.SLIDE,
                playOnAppear = true,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun SwipingStep(
    uiState: DeckUiState,
    onSwiped: (DeckEntry, Boolean) -> Unit,
    onFinishEarly: () -> Unit,
) {
    val stapel = rememberSwipeCardStackState()
    Column(modifier = Modifier.fillMaxSize()) {
        DeckProgress(uiState = uiState)

        if (uiState.isEmpty) {
            // Die naechsten Karten werden noch geprueft oder ihre Bilder geholt.
            RezeptliLoader(modifier = Modifier.weight(1f))
        } else {
            SwipeCardStack(
                cards = uiState.remainingCards,
                onSwiped = { summary, liked ->
                    // Der Stapel kennt nur RecipeSummary; die Kennung ist die Adresse.
                    val eintrag = uiState.cards.firstOrNull { it.url.hashCode().toLong() == summary.id }
                    if (eintrag != null) onSwiped(eintrag, liked)
                },
                state = stapel,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        SwipeDecisionButtons(
            onNo = { stapel.swipe(false) },
            onYes = { stapel.swipe(true) },
            enabled = !uiState.isEmpty,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )

        // Ohne Ziel gibt es keine Zahl, bei der die Runde von selbst endet - dafuer
        // dieser eigene, gut sichtbare Knopf statt der kleinen Textschaltflaeche oben.
        if (uiState.target == null) {
            BouncyOutlinedButton(
                onClick = onFinishEarly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Das Herz springt, sobald etwas Neues gefunden ist.
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = RezeptliTheme.accents.tomato,
                modifier = Modifier
                    .size(18.dp)
                    .popOnChange(uiState.foundCount),
            )
            val ziel = uiState.target
            Text(
                text = if (ziel != null) {
                    stringResource(R.string.stapel_fortschritt, uiState.foundCount, ziel)
                } else {
                    stringResource(R.string.stapel_fortschritt_endlos, uiState.foundCount)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (uiState.target != null) {
            RezeptliProgressBar(progress = uiState.progress, color = RezeptliTheme.accents.tomato)
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

    val eintritt = rememberEntranceState()
    Box(modifier = Modifier.fillMaxSize()) {
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
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.entrance(0, eintritt, "kopf"),
                )
            }

            itemsIndexed(uiState.liked, key = { _, eintrag -> eintrag.url }) { index, eintrag ->
                LikedRow(
                    eintrag = eintrag,
                    modifier = Modifier.entrance(index + 1, eintritt, eintrag.url),
                )
            }

            item(key = "aktionen") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .entrance(uiState.liked.size + 1, eintritt, "aktionen"),
                ) {
                    BouncyButton(
                        onClick = onKeep,
                        enabled = !uiState.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
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
                    BouncyOutlinedButton(
                        onClick = onTogether,
                        enabled = !uiState.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        MotionIcon(
                            imageVector = Icons.Filled.Group,
                            contentDescription = null,
                            motion = IconMotion.HOP,
                            playOnAppear = true,
                        )
                        Text(
                            text = stringResource(R.string.stapel_zu_zweit),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }

                    BouncyTextButton(
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

        // Geschafft - ein kurzer Konfettiregen ueber der Ausbeute.
        ConfettiBurst(trigger = uiState.liked.size, modifier = Modifier.fillMaxSize())
    }
}

/** Ein gemochtes Rezept: kleines Foto, Titel, Quelle. */
@Composable
private fun LikedRow(eintrag: DeckEntry, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(Tokens.Radius.LgShape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeImage(
            photoUri = eintrag.imageUrl,
            title = eintrag.title,
            modifier = Modifier
                .size(60.dp)
                .clip(Tokens.Radius.MdShape),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                eintrag.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                eintrag.sourceName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = RezeptliTheme.accents.tomato,
            modifier = Modifier.size(20.dp),
        )
    }
}

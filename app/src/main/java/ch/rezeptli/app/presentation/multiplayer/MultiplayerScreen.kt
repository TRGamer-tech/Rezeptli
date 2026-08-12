package ch.rezeptli.app.presentation.multiplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.shareText
import ch.rezeptli.app.presentation.common.theme.Tokens
import ch.rezeptli.app.presentation.common.theme.cardSurface
import ch.rezeptli.app.presentation.common.theme.glassPanel
import ch.rezeptli.app.presentation.deck.TARGET_OPTIONS
import ch.rezeptli.app.presentation.swipe.SwipeCardStack

/**
 * Gemeinsam entscheiden: eine Person eroeffnet, die andere tritt mit dem Code bei.
 *
 * Beide bringen einen eigenen Vorschlag aus dem Verzeichnis mit; der Dienst mischt
 * beide zu einem gemeinsamen Topf. Die Treffer erscheinen erst, wenn beide fertig
 * sind - sonst koennte man am Zwischenstand ablesen, was die andere Person gewischt
 * hat, und das ist beim gemeinsamen Aussuchen der halbe Reiz.
 */
@Composable
fun MultiplayerRoute(
    onBack: () -> Unit,
    viewModel: MultiplayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MultiplayerScreen(
        uiState = uiState,
        onBack = {
            viewModel.onLeave()
            onBack()
        },
        onTargetChange = viewModel::onTargetChange,
        onContinueFromTarget = viewModel::onContinueFromTarget,
        onHost = viewModel::onHost,
        onCodeInputChange = viewModel::onCodeInputChange,
        onJoin = viewModel::onJoin,
        onStartSwiping = viewModel::onStartSwiping,
        onSwiped = viewModel::onSwiped,
        onDismissError = viewModel::onDismissError,
        onKeep = viewModel::onKeepMatches,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiplayerScreen(
    uiState: MultiplayerUiState,
    onBack: () -> Unit,
    onTargetChange: (Int) -> Unit,
    onContinueFromTarget: () -> Unit,
    onHost: () -> Unit,
    onCodeInputChange: (String) -> Unit,
    onJoin: () -> Unit,
    onStartSwiping: () -> Unit,
    onSwiped: (Long, Boolean) -> Unit,
    onDismissError: () -> Unit,
    onKeep: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            RezeptliTopBar(
                title = stringResource(R.string.mehrspieler_titel),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            uiState.error?.let { error ->
                ErrorNote(error = error, onDismiss = onDismissError)
            }

            when (uiState.step) {
                MultiplayerStep.ANZAHL -> TargetStep(
                    uiState = uiState,
                    onTargetChange = onTargetChange,
                    onContinue = onContinueFromTarget,
                )

                MultiplayerStep.START -> StartStep(
                    uiState = uiState,
                    onHost = onHost,
                    onCodeInputChange = onCodeInputChange,
                    onJoin = onJoin,
                )

                MultiplayerStep.WARTET_AUF_PERSON -> WaitingForPartner(
                    code = uiState.code,
                    onStartSwiping = onStartSwiping,
                )

                MultiplayerStep.WISCHEN -> SwipingStep(uiState = uiState, onSwiped = onSwiped)

                MultiplayerStep.WARTET_AUF_ENTSCHEIDUNGEN -> WaitingNote(
                    title = stringResource(R.string.mehrspieler_warten_titel),
                    message = stringResource(R.string.mehrspieler_warten_text),
                )

                MultiplayerStep.TREFFER -> MatchesStep(uiState = uiState, onKeep = onKeep)
            }
        }
    }
}

/** Wie viele Gerichte soll der eigene Vorschlag zur gemeinsamen Runde beitragen? */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetStep(
    uiState: MultiplayerUiState,
    onTargetChange: (Int) -> Unit,
    onContinue: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.stapel_anzahl_frage),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.mehrspieler_anzahl_hinweis),
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
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.mehrspieler_weiter))
        }
    }
}

@Composable
private fun StartStep(
    uiState: MultiplayerUiState,
    onHost: () -> Unit,
    onCodeInputChange: (String) -> Unit,
    onJoin: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.mehrspieler_einleitung),
            style = MaterialTheme.typography.bodyLarge,
        )
        // Ehrlich sagen, was passiert - hier verlassen zum ersten Mal Rezepttitel
        // das Geraet.
        Text(
            stringResource(R.string.mehrspieler_datenschutz),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onHost,
            enabled = !uiState.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.mehrspieler_einladen))
        }

        Text(
            stringResource(R.string.mehrspieler_oder),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        OutlinedTextField(
            value = uiState.codeInput,
            onValueChange = onCodeInputChange,
            label = { Text(stringResource(R.string.mehrspieler_code_eingeben)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = onJoin,
            enabled = uiState.codeInput.isNotBlank() && !uiState.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.mehrspieler_beitreten))
        }

        if (uiState.isBusy) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun WaitingForPartner(code: String, onStartSwiping: () -> Unit) {
    val context = LocalContext.current
    val shareMessage = stringResource(R.string.mehrspieler_teilen_text, code)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.mehrspieler_code_zeigen),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        // Der Code ist das Wichtigste auf diesem Bildschirm - entsprechend gross,
        // mit Abstand zwischen den Zeichen, damit man ihn vorlesen kann.
        Text(
            text = code,
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                letterSpacing = 8.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(Tokens.Radius.XxlShape)
                .padding(vertical = 24.dp),
            textAlign = TextAlign.Center,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { context.shareText(shareMessage) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                Text(
                    text = "  " + stringResource(R.string.mehrspieler_teilen),
                )
            }
            Button(onClick = onStartSwiping, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.mehrspieler_schon_loswischen))
            }
        }

        WaitingNote(
            title = stringResource(R.string.mehrspieler_warte_person_titel),
            message = stringResource(R.string.mehrspieler_warte_person_text),
        )
    }
}

@Composable
private fun SwipingStep(uiState: MultiplayerUiState, onSwiped: (Long, Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = uiState.progressLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.weight(1f)) {
            SwipeCardStack(
                cards = uiState.remainingCards,
                onSwiped = { card, liked -> onSwiped(card.id, liked) },
            )
        }
    }
}

@Composable
private fun MatchesStep(uiState: MultiplayerUiState, onKeep: () -> Unit) {
    if (uiState.matches.isEmpty()) {
        WaitingNote(
            title = stringResource(R.string.mehrspieler_keine_treffer_titel),
            message = stringResource(R.string.mehrspieler_keine_treffer_text),
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.mehrspieler_treffer_titel),
            style = MaterialTheme.typography.headlineSmall,
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            items(uiState.matches, key = { it.recipeId }) { match ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cardSurface(Tokens.Radius.LgShape)
                        .padding(16.dp),
                ) {
                    Text(match.title, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // Bis hierhin waren die Treffer nur Titel. Ein Griff bringt sie in die
        // Sammlung und ihre Zutaten auf die Einkaufsliste.
        val fertig = uiState.addedItems
        if (fertig == null) {
            Button(
                onClick = onKeep,
                enabled = !uiState.isKeeping,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    if (uiState.isKeeping) {
                        stringResource(
                            R.string.mehrspieler_uebernehmen_laeuft,
                            uiState.keptRecipes,
                            uiState.matches.size,
                        )
                    } else {
                        stringResource(R.string.mehrspieler_uebernehmen)
                    },
                )
            }
        } else {
            Text(
                text = pluralStringResource(R.plurals.mehrspieler_uebernommen, fertig, fertig),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WaitingNote(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(Tokens.Radius.LgShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.heightIn(min = 24.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorNote(error: PairingError, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(Tokens.Radius.MdShape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.action_ok))
        }
    }
}

private fun PairingError.messageRes(): Int = when (this) {
    PairingError.NO_CONNECTION -> R.string.mehrspieler_fehler_verbindung
    PairingError.UNKNOWN_CODE -> R.string.mehrspieler_fehler_code
    PairingError.EXPIRED -> R.string.mehrspieler_fehler_abgelaufen
    PairingError.FULL -> R.string.mehrspieler_fehler_voll
    PairingError.NO_RECIPES -> R.string.mehrspieler_fehler_keine_rezepte
    PairingError.UNKNOWN -> R.string.mehrspieler_fehler_unbekannt
}

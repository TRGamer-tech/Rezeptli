package ch.rezeptli.app.presentation.multiplayer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.RoundEnd
import ch.rezeptli.app.domain.multiplayer.RoundSource
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.plan.CourseChoice
import ch.rezeptli.app.domain.plan.DietaryFilter
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.presentation.common.components.BouncyButton
import ch.rezeptli.app.presentation.common.components.BouncyFilledTonalButton
import ch.rezeptli.app.presentation.common.components.BouncyOutlinedButton
import ch.rezeptli.app.presentation.common.components.BouncyTextButton
import ch.rezeptli.app.presentation.common.components.ConfettiBurst
import ch.rezeptli.app.presentation.common.components.CookingPot
import ch.rezeptli.app.presentation.common.components.IconMotion
import ch.rezeptli.app.presentation.common.components.MotionIcon
import ch.rezeptli.app.presentation.common.components.PortionDialog
import ch.rezeptli.app.presentation.common.components.RecipeImage
import ch.rezeptli.app.presentation.common.components.RezeptliFilterChip
import ch.rezeptli.app.presentation.common.components.RezeptliLoader
import ch.rezeptli.app.presentation.common.components.RezeptliProgressBar
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.components.SectionCard
import ch.rezeptli.app.presentation.common.components.entrance
import ch.rezeptli.app.presentation.common.components.popOnChange
import ch.rezeptli.app.presentation.common.components.rememberEntranceState
import ch.rezeptli.app.presentation.common.label
import ch.rezeptli.app.presentation.common.shareText
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.Tokens
import ch.rezeptli.app.presentation.common.theme.cardSurface
import ch.rezeptli.app.presentation.common.theme.highlightRing
import ch.rezeptli.app.presentation.deck.TARGET_OPTIONS
import ch.rezeptli.app.presentation.plan.DietPicker
import ch.rezeptli.app.presentation.plan.DislikePicker
import ch.rezeptli.app.presentation.plan.IntolerancePicker
import ch.rezeptli.app.presentation.swipe.RecipeInfoSheet
import ch.rezeptli.app.presentation.swipe.SwipeCardStack
import ch.rezeptli.app.presentation.swipe.SwipeDecisionButtons
import ch.rezeptli.app.presentation.swipe.rememberSwipeCardStackState

/** Was die Oberflaeche an den Bildschirm weiterreicht - gebuendelt, damit die Liste lesbar bleibt. */
data class MultiplayerActions(
    val onBack: () -> Unit = {},
    val onHost: () -> Unit = {},
    val onCodeInputChange: (String) -> Unit = {},
    val onJoin: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onOwnMainDiet: (Diet?) -> Unit = {},
    val onOwnDietToggle: (Diet) -> Unit = {},
    val onOwnIntoleranceToggle: (Intolerance) -> Unit = {},
    val onOwnDislikeToggle: (Dislike) -> Unit = {},
    val onOwnCustomDislikeAdd: (String) -> Unit = {},
    val onOwnCustomDislikeRemove: (String) -> Unit = {},
    val onTargetChange: (Int) -> Unit = {},
    val onCourseChange: (CourseChoice) -> Unit = {},
    val onSourceChange: (RoundSource) -> Unit = {},
    val onStart: () -> Unit = {},
    val onSwiped: (Long, Boolean) -> Unit = { _, _ -> },
    val onUndo: () -> Unit = {},
    val onInfo: (Long) -> Unit = {},
    val onInfoDismiss: () -> Unit = {},
    val onContinue: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onKeep: () -> Unit = {},
    val onAddToPlan: () -> Unit = {},
    val onRetryImport: () -> Unit = {},
    val onReplaceInPlan: () -> Unit = {},
    val onKeepPlanAsIs: () -> Unit = {},
    val onListRequest: () -> Unit = {},
    val onListDismiss: () -> Unit = {},
    val onAddToShoppingList: (Int) -> Unit = {},
    val onNewRound: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

/**
 * Gemeinsam entscheiden: eine Person eroeffnet, die andere tritt mit dem Code bei.
 *
 * Der Code steht zuerst. Dann legt fest, wer eroeffnet hat, wie viele Gerichte gesucht
 * werden - die eingeladene Person sieht es mit. Beide wischen denselben Stapel; sobald
 * genug gemeinsame Treffer da sind, ist Schluss.
 */
@Composable
fun MultiplayerRoute(
    onBack: () -> Unit,
    viewModel: MultiplayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var verlassenFragen by remember { mutableStateOf(false) }
    val laeuft = uiState.isInRound

    // Mitten in einer Runde fuehrt "Zurueck" nicht einfach hinaus - ein versehentlicher
    // Wisch vom Rand haette sonst die ganze Runde gekostet.
    BackHandler(enabled = laeuft) { verlassenFragen = true }

    if (verlassenFragen) {
        LeaveDialog(
            code = uiState.code,
            onPause = {
                verlassenFragen = false
                viewModel.onLeave()
                onBack()
            },
            onEnd = {
                verlassenFragen = false
                // Aus der Lobby gibt es nichts zu zeigen - dann geht es gleich hinaus. Sonst
                // bleibt man fuer die Treffer, die bis hierhin gefunden wurden.
                val nichtsZuZeigen = uiState.step == MultiplayerStep.LOBBY
                viewModel.onEndRound()
                if (nichtsZuZeigen) onBack()
            },
            onDismiss = { verlassenFragen = false },
        )
    }

    MultiplayerScreen(
        uiState = uiState,
        actions = MultiplayerActions(
            onBack = {
                if (laeuft) {
                    verlassenFragen = true
                } else {
                    viewModel.onLeave()
                    onBack()
                }
            },
            onHost = viewModel::onHost,
            onCodeInputChange = viewModel::onCodeInputChange,
            onJoin = viewModel::onJoin,
            onResume = viewModel::onResume,
            onOwnMainDiet = viewModel::onOwnMainDiet,
            onOwnDietToggle = viewModel::onOwnDietToggle,
            onOwnIntoleranceToggle = viewModel::onOwnIntoleranceToggle,
            onOwnDislikeToggle = viewModel::onOwnDislikeToggle,
            onOwnCustomDislikeAdd = viewModel::onOwnCustomDislikeAdd,
            onOwnCustomDislikeRemove = viewModel::onOwnCustomDislikeRemove,
            onTargetChange = viewModel::onTargetChange,
            onCourseChange = viewModel::onCourseChange,
            onSourceChange = viewModel::onSourceChange,
            onStart = viewModel::onStart,
            onSwiped = viewModel::onSwiped,
            onUndo = viewModel::onUndo,
            onInfo = viewModel::onInfoRequest,
            onInfoDismiss = viewModel::onInfoDismiss,
            onContinue = viewModel::onContinue,
            onStop = viewModel::onEndRound,
            onKeep = viewModel::onKeepMatches,
            onRetryImport = viewModel::onRetryImport,
            onReplaceInPlan = viewModel::onReplaceInPlan,
            onKeepPlanAsIs = viewModel::onKeepPlanAsIs,
            onListRequest = viewModel::onListRequest,
            onListDismiss = viewModel::onListDismiss,
            onAddToShoppingList = viewModel::onAddToShoppingList,
            onAddToPlan = viewModel::onAddToPlan,
            onNewRound = viewModel::onNewRound,
            onDismissError = viewModel::onDismissError,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiplayerScreen(
    uiState: MultiplayerUiState,
    actions: MultiplayerActions,
) {
    val motion = RezeptliTheme.motion

    uiState.info?.let { info -> RecipeInfoSheet(info = info, onDismiss = actions.onInfoDismiss) }

    Scaffold(
        topBar = {
            RezeptliTopBar(
                title = stringResource(R.string.mehrspieler_titel),
                onBack = actions.onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            uiState.error?.let { error ->
                ErrorNote(error = error, onDismiss = actions.onDismissError)
            }

            // Fehlt nur das Netz, laeuft die Runde weiter - das soll man wissen, statt zu raetseln.
            AnimatedVisibility(visible = uiState.isOffline && uiState.step != MultiplayerStep.START) {
                Text(
                    text = stringResource(R.string.zuzweit_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .cardSurface(Tokens.Radius.MdShape)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            // Jeder Schritt kommt mit einer kleinen Bewegung herein: Einladen, Warten,
            // Wischen und die Treffer fuehlen sich so wie ein Weg an, nicht wie Spruenge.
            AnimatedContent(
                targetState = uiState.step,
                transitionSpec = { motion.enter() togetherWith motion.exit() },
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "zuzweit",
            ) { step ->
                when (step) {
                    MultiplayerStep.START -> StartStep(uiState = uiState, actions = actions)

                    MultiplayerStep.LOBBY -> if (uiState.isHost) {
                        HostLobby(uiState = uiState, actions = actions)
                    } else {
                        GuestLobby(uiState = uiState)
                    }

                    MultiplayerStep.SAMMELN -> CollectingStep(uiState = uiState)

                    MultiplayerStep.WISCHEN -> SwipingStep(uiState = uiState, actions = actions)

                    MultiplayerStep.WARTET -> WaitingStep(uiState = uiState, onUndo = actions.onUndo)

                    MultiplayerStep.FRAGE -> QuestionStep(uiState = uiState, actions = actions)

                    MultiplayerStep.TREFFER -> MatchesStep(uiState = uiState, actions = actions)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------- Anfang

@Composable
private fun StartStep(uiState: MultiplayerUiState, actions: MultiplayerActions) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        uiState.resumableCode?.let { code ->
            ResumeCard(code = code, isBusy = uiState.isBusy, onResume = actions.onResume)
        }

        Text(
            stringResource(R.string.mehrspieler_einleitung),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.entrance(0),
        )

        if (uiState.selectionCount > 0) {
            Text(
                pluralStringResource(
                    R.plurals.zuzweit_auswahl_kommt_mit,
                    uiState.selectionCount,
                    uiState.selectionCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        OwnRestrictions(uiState = uiState, actions = actions)

        BouncyButton(
            onClick = actions.onHost,
            enabled = !uiState.isBusy,
            modifier = Modifier
                .entrance(2)
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            MotionIcon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                motion = IconMotion.WIGGLE,
                playOnAppear = true,
                modifier = Modifier.size(20.dp),
            )
            Text(stringResource(R.string.mehrspieler_einladen), modifier = Modifier.padding(start = 8.dp))
        }

        Text(
            stringResource(R.string.mehrspieler_oder),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .entrance(3)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        OutlinedTextField(
            value = uiState.codeInput,
            onValueChange = actions.onCodeInputChange,
            label = { Text(stringResource(R.string.mehrspieler_code_eingeben)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            textStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .entrance(4)
                .fillMaxWidth(),
        )
        BouncyOutlinedButton(
            onClick = actions.onJoin,
            enabled = uiState.codeInput.isNotBlank() && !uiState.isBusy,
            modifier = Modifier
                .entrance(5)
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text(stringResource(R.string.mehrspieler_beitreten))
        }

        if (uiState.isBusy) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CookingPot(size = 56.dp)
            }
        }

        // Ehrlich sagen, was passiert - hier verlassen Rezepttitel und Angaben zur
        // Ernaehrung das Geraet.
        Text(
            stringResource(R.string.mehrspieler_datenschutz),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .entrance(6)
                .padding(bottom = 16.dp),
        )
    }
}

/**
 * Was man selbst in die Runde mitbringt.
 *
 * Zusammengeklappt steht da nur, was gilt - die meisten aendern nichts, die Vorlieben
 * passen schon. Aufgeklappt sind es drei getrennte Gruppen mit je einem Satz dazu, was sie
 * bedeuten: Frueher standen "Vegan" und "Nuesse" unter derselben Frage "Was darf nicht auf
 * den Tisch?" - das eine hiess aber "nur vegan", das andere "keine Nuesse".
 */
@Composable
private fun OwnRestrictions(uiState: MultiplayerUiState, actions: MultiplayerActions) {
    var offen by rememberSaveable { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.zuzweit_eigene_titel), index = 1) {
        Text(
            stringResource(R.string.zuzweit_eigene_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!offen) {
            RestrictionSummary(filter = uiState.ownRestrictions)
        } else {
            RestrictionGroup(R.string.zuzweit_gruppe_ernaehrung, R.string.zuzweit_gruppe_ernaehrung_text) {
                DietPicker(
                    profile = uiState.own,
                    onMainDiet = actions.onOwnMainDiet,
                    onToggleDiet = actions.onOwnDietToggle,
                )
            }
            RestrictionGroup(R.string.zuzweit_gruppe_unvertraeglich, R.string.zuzweit_gruppe_unvertraeglich_text) {
                IntolerancePicker(
                    selected = uiState.own.intolerances,
                    onToggle = actions.onOwnIntoleranceToggle,
                )
            }
            RestrictionGroup(R.string.zuzweit_gruppe_abneigungen, R.string.zuzweit_gruppe_abneigungen_text) {
                DislikePicker(
                    selected = uiState.own.dislikes,
                    custom = uiState.own.customDislikes,
                    onToggle = actions.onOwnDislikeToggle,
                    onAddCustom = actions.onOwnCustomDislikeAdd,
                    onRemoveCustom = actions.onOwnCustomDislikeRemove,
                )
            }
        }

        BouncyTextButton(onClick = { offen = !offen }) {
            Text(stringResource(if (offen) R.string.zuzweit_eigene_fertig else R.string.zuzweit_eigene_anpassen))
        }
    }
}

@Composable
private fun RestrictionGroup(titleRes: Int, textRes: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(textRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/**
 * Was gilt, in hoechstens drei Zeilen - jede sagt dazu, ob sie eine Bedingung oder ein
 * Ausschluss ist.
 */
@Composable
private fun RestrictionSummary(filter: DietaryFilter, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!filter.isActive) {
            Text(stringResource(R.string.zuzweit_keine_angaben), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        val ernaehrung = filter.diets.map { it.label() }
        val unvertraeglich = filter.intolerances.map { it.label() }
        val abneigungen = filter.dislikes.map { it.label() } + filter.customDislikes

        if (ernaehrung.isNotEmpty()) {
            SummaryLine(stringResource(R.string.zuzweit_zeile_ernaehrung, ernaehrung.joinToString(", ")))
        }
        if (unvertraeglich.isNotEmpty()) {
            SummaryLine(stringResource(R.string.zuzweit_zeile_unvertraeglich, unvertraeglich.joinToString(", ")))
        }
        if (abneigungen.isNotEmpty()) {
            SummaryLine(stringResource(R.string.zuzweit_zeile_abneigungen, abneigungen.joinToString(", ")))
        }
    }
}

@Composable
private fun SummaryLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

/** Eine unterbrochene Runde - ein Tipp, und es geht mit demselben Stapel weiter. */
@Composable
private fun ResumeCard(code: String, isBusy: Boolean, onResume: () -> Unit) {
    Column(
        modifier = Modifier
            .entrance(0)
            .fillMaxWidth()
            .cardSurface(Tokens.Radius.LgShape)
            .highlightRing(Tokens.Radius.LgShape, intensity = 0.5f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.zuzweit_fortsetzen_titel, code), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.zuzweit_fortsetzen_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BouncyButton(
            onClick = onResume,
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            MotionIcon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                motion = IconMotion.POP,
                playOnAppear = true,
                modifier = Modifier.size(20.dp),
            )
            Text(stringResource(R.string.zuzweit_fortsetzen), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun LeaveDialog(code: String, onPause: () -> Unit, onEnd: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zuzweit_verlassen_titel)) },
        text = { Text(stringResource(R.string.zuzweit_verlassen_text, code)) },
        confirmButton = {
            BouncyTextButton(onClick = onDismiss) { Text(stringResource(R.string.zuzweit_weiterwischen)) }
        },
        dismissButton = {
            Row {
                BouncyTextButton(onClick = onEnd) {
                    Text(stringResource(R.string.zuzweit_beenden), color = MaterialTheme.colorScheme.error)
                }
                BouncyTextButton(onClick = onPause) { Text(stringResource(R.string.zuzweit_unterbrechen)) }
            }
        },
    )
}

// ------------------------------------------------------------------------- Lobby

/**
 * Die Lobby der Person, die eroeffnet hat: oben der Code, darunter, was gesucht wird.
 *
 * Der Code steht schon, waehrend noch gewaehlt wird - so kann die andere Person beitreten
 * und zusehen, statt auf eine fertige Runde zu warten.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostLobby(uiState: MultiplayerUiState, actions: MultiplayerActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CodeCard(code = uiState.code)
        PartnerStatus(isHere = !uiState.isAlone)

        SectionCard(title = stringResource(R.string.zuzweit_lobby_anzahl), index = 1) {
            Text(
                stringResource(R.string.zuzweit_lobby_anzahl_text),
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
                        onClick = { actions.onTargetChange(anzahl) },
                        label = pluralStringResource(R.plurals.stapel_gerichte, anzahl, anzahl),
                    )
                }
            }
        }

        SectionCard(title = stringResource(R.string.zuzweit_lobby_art), index = 2) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CourseChoice.entries.forEach { art ->
                    RezeptliFilterChip(
                        selected = uiState.course == art,
                        onClick = { actions.onCourseChange(art) },
                        label = stringResource(art.labelRes()),
                    )
                }
            }
        }

        SectionCard(title = stringResource(R.string.zuzweit_lobby_quelle), index = 3) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RoundSource.entries.forEach { quelle ->
                    RezeptliFilterChip(
                        selected = uiState.source == quelle,
                        onClick = { actions.onSourceChange(quelle) },
                        label = stringResource(quelle.labelRes()),
                    )
                }
            }
            if (uiState.source == RoundSource.GESPEICHERT) {
                Text(
                    stringResource(R.string.zuzweit_quelle_hinweis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SharedRestrictions(filter = uiState.restrictions, index = 4)

        BouncyButton(
            onClick = actions.onStart,
            enabled = !uiState.isAlone && !uiState.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.stapel_losgehts), style = MaterialTheme.typography.titleMedium)
            MotionIcon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                motion = IconMotion.SLIDE,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp),
            )
        }
        if (uiState.isAlone) {
            Text(
                stringResource(R.string.zuzweit_lobby_start_allein),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

/**
 * Die Lobby der eingeladenen Person: Sie sieht, was festgelegt wird, kann es aber nicht
 * aendern - die Anzahl bestimmt, wer eroeffnet hat.
 */
@Composable
private fun GuestLobby(uiState: MultiplayerUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WaitingNote(
            title = stringResource(R.string.zuzweit_gast_titel),
            message = stringResource(R.string.zuzweit_gast_text),
        )

        SectionCard(title = stringResource(R.string.zuzweit_gast_gesucht), index = 1) {
            // Die Zahl springt kurz, wenn sie sich aendert - man sieht, dass gerade gewaehlt wird.
            Text(
                text = pluralStringResource(R.plurals.stapel_gerichte, uiState.target, uiState.target) +
                    " · " + stringResource(uiState.course.labelRes()),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.popOnChange(uiState.target to uiState.course),
            )
            Text(
                text = stringResource(
                    if (uiState.source == RoundSource.GESPEICHERT) {
                        R.string.zuzweit_gast_quelle_gespeichert
                    } else {
                        R.string.zuzweit_gast_quelle_neu
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SharedRestrictions(filter = uiState.restrictions, index = 2)
    }
}

/** Was fuer beide gilt - die vereinigten Angaben, damit niemand ueberrascht wird, was fehlt. */
@Composable
private fun SharedRestrictions(filter: DietaryFilter, index: Int) {
    SectionCard(title = stringResource(R.string.zuzweit_gilt_titel), index = index) {
        RestrictionSummary(filter = filter)
        if (filter.isActive) {
            Text(
                stringResource(R.string.zuzweit_pruef_hinweis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PartnerStatus(isHere: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(Tokens.Radius.MdShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isHere) {
            MotionIcon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                motion = IconMotion.POP,
                playOnAppear = true,
                tint = RezeptliTheme.accents.swipeYes,
                modifier = Modifier.size(22.dp),
            )
        } else {
            CookingPot(size = 28.dp)
        }
        Text(
            text = stringResource(if (isHere) R.string.zuzweit_lobby_da else R.string.zuzweit_lobby_wartet),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun CodeCard(code: String) {
    val context = LocalContext.current
    val shareMessage = stringResource(R.string.mehrspieler_teilen_text, code)
    val motion = RezeptliTheme.motion

    // Der Code atmet sanft - es wird gewartet, aber nichts ist kaputt.
    val atmen = if (motion.reduced) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "code")
        val wert by transition.animateFloat(
            initialValue = 1f,
            targetValue = BREATH_SCALE,
            animationSpec = infiniteRepeatable(tween(BREATH_MS, easing = Tokens.Ease.Standard), RepeatMode.Reverse),
            label = "atmen",
        )
        wert
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.mehrspieler_code_zeigen),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.entrance(0),
        )

        // Der Code ist das Wichtigste auf diesem Bildschirm - entsprechend gross,
        // mit Abstand zwischen den Zeichen, damit man ihn vorlesen kann.
        Text(
            text = code,
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
                letterSpacing = 8.sp,
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .entrance(1)
                .graphicsLayer {
                    scaleX = atmen
                    scaleY = atmen
                }.fillMaxWidth()
                .highlightRing(Tokens.Radius.XlShape, intensity = 0.4f)
                .clip(Tokens.Radius.XlShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(vertical = 20.dp),
            textAlign = TextAlign.Center,
        )

        BouncyOutlinedButton(
            onClick = { context.shareText(shareMessage) },
            modifier = Modifier
                .entrance(2)
                .heightIn(min = 48.dp),
        ) {
            MotionIcon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                motion = IconMotion.WIGGLE,
                playOnAppear = true,
            )
            Text(
                text = stringResource(R.string.mehrspieler_teilen),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun CourseChoice.labelRes(): Int = when (this) {
    CourseChoice.ESSEN -> R.string.stapel_art_essen
    CourseChoice.GETRAENKE -> R.string.stapel_art_getraenke
    CourseChoice.BEIDES -> R.string.stapel_art_beides
}

private fun RoundSource.labelRes(): Int = when (this) {
    RoundSource.GESPEICHERT -> R.string.zuzweit_quelle_gespeichert
    RoundSource.NEU -> R.string.zuzweit_quelle_neu
}

// ------------------------------------------------------------------------- Sammeln und Wischen

@Composable
private fun CollectingStep(uiState: MultiplayerUiState) {
    val rest = uiState.waitSeconds
    val zustand = stringResource(
        when {
            uiState.hasContributed -> R.string.zuzweit_sammeln_wartet
            uiState.source == RoundSource.GESPEICHERT -> R.string.zuzweit_sammeln_gespeichert
            else -> R.string.zuzweit_sammeln_neu
        },
    )
    // Niemand soll vor einem Ladebalken sitzen, weil das andere Telefon in der Tasche steckt:
    // Laeuft die Frist, steht hier, wie lange es noch dauert und was danach geschieht.
    val frist = when {
        rest == null -> null
        uiState.hasContributed -> pluralStringResource(R.plurals.zuzweit_frist_ich_bereit, rest, rest)
        else -> pluralStringResource(R.plurals.zuzweit_frist_andere_bereit, rest, rest)
    }
    WaitingNote(
        title = stringResource(R.string.zuzweit_sammeln_titel),
        message = listOfNotNull(zustand, frist).joinToString(" "),
    )
}

@Composable
private fun SwipingStep(uiState: MultiplayerUiState, actions: MultiplayerActions) {
    val stapel = rememberSwipeCardStackState()
    val offen = uiState.remainingCards
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RoundProgress(uiState = uiState, open = offen.size)

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (offen.isEmpty()) {
                RezeptliLoader()
            }
            SwipeCardStack(
                cards = offen,
                onSwiped = { card, liked -> actions.onSwiped(card.id, liked) },
                state = stapel,
                onInfo = { card -> actions.onInfo(card.id) },
            )
        }
        SwipeDecisionButtons(
            onNo = { stapel.swipe(false) },
            onYes = { stapel.swipe(true) },
            enabled = offen.isNotEmpty(),
            onUndo = actions.onUndo,
            canUndo = uiState.canUndo,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )
    }
}

/** Wie weit die Runde ist: gemeinsame Treffer gegen das Ziel, und wie viele Karten noch offen sind. */
@Composable
private fun RoundProgress(uiState: MultiplayerUiState, open: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Das Herz springt, sobald ein gemeinsamer Treffer dazukommt.
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = RezeptliTheme.accents.tomato,
                modifier = Modifier
                    .size(18.dp)
                    .popOnChange(uiState.matchCount),
            )
            Text(
                text = stringResource(R.string.zuzweit_fortschritt, uiState.matchCount, uiState.target),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pluralStringResource(R.plurals.zuzweit_karten_offen, open, open),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RezeptliProgressBar(progress = uiState.progress, color = RezeptliTheme.accents.tomato)
    }
}

/** Selbst durch - die andere Person wischt noch. Ein Versehen laesst sich auch hier zuruecknehmen. */
@Composable
private fun WaitingStep(uiState: MultiplayerUiState, onUndo: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RoundProgress(uiState = uiState, open = 0)
        WaitingNote(
            title = stringResource(R.string.mehrspieler_warten_titel),
            message = stringResource(R.string.mehrspieler_warten_text),
        )
        if (uiState.canUndo) {
            BouncyTextButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(stringResource(R.string.wischen_zuruecknehmen), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

// ------------------------------------------------------------------------- Frage und Ende

/**
 * Der Stapel ist durch, das Ziel nicht erreicht.
 *
 * Wer eroeffnet hat, entscheidet: mit neuen Rezepten weitermachen oder aufhoeren und
 * behalten, was gefunden wurde. Die andere Person sieht dieselben Treffer und wartet.
 */
@Composable
private fun QuestionStep(uiState: MultiplayerUiState, actions: MultiplayerActions) {
    val eintritt = rememberEntranceState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "kopf") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.zuzweit_frage_titel), style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = stringResource(
                        if (uiState.source == RoundSource.GESPEICHERT) {
                            R.string.zuzweit_frage_gespeichert
                        } else {
                            R.string.zuzweit_frage_neu
                        },
                        uiState.matchCount,
                        uiState.target,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        if (uiState.matches.isNotEmpty()) {
            item(key = "bisher") {
                Text(
                    stringResource(R.string.zuzweit_bisher),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            itemsIndexed(uiState.matches, key = { _, match -> match.recipeId }) { index, match ->
                MatchRow(match = match, modifier = Modifier.entrance(index, eintritt, match.recipeId))
            }
        }

        item(key = "entscheidung") {
            if (uiState.isHost) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    BouncyButton(
                        onClick = actions.onContinue,
                        enabled = !uiState.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.zuzweit_frage_weiter)) }

                    BouncyOutlinedButton(
                        onClick = actions.onStop,
                        enabled = !uiState.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        Text(
                            stringResource(
                                if (uiState.matches.isEmpty()) {
                                    R.string.zuzweit_frage_aufhoeren_leer
                                } else {
                                    R.string.zuzweit_frage_aufhoeren
                                },
                            ),
                        )
                    }
                }
            } else {
                WaitingNote(
                    title = stringResource(R.string.zuzweit_frage_titel),
                    message = stringResource(R.string.zuzweit_frage_gast),
                )
            }
        }
    }
}

@Composable
private fun MatchesStep(uiState: MultiplayerUiState, actions: MultiplayerActions) {
    val grund = uiState.end?.let { ende -> stringResource(ende.messageRes(uiState.isHost)) }

    if (uiState.matches.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WaitingNote(
                title = stringResource(R.string.mehrspieler_keine_treffer_titel),
                message = listOfNotNull(
                    grund.takeIf { uiState.end != RoundEnd.ZIEL },
                    stringResource(R.string.mehrspieler_keine_treffer_text),
                ).joinToString(" "),
                busy = false,
            )
            BouncyButton(
                onClick = actions.onNewRound,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.zuzweit_neue_runde)) }
        }
        return
    }

    val eintritt = rememberEntranceState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "kopf") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.entrance(0)) {
                    Text(
                        stringResource(R.string.mehrspieler_treffer_titel),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (grund != null) {
                        Text(
                            grund,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            itemsIndexed(uiState.matches, key = { _, match -> match.recipeId }) { index, match ->
                MatchRow(
                    match = match,
                    modifier = Modifier.entrance(index + 1, eintritt, match.recipeId),
                )
            }

            item(key = "aktionen") {
                MatchActions(uiState = uiState, actions = actions)
            }
        }

        // Ihr habt euch gefunden - Konfetti.
        ConfettiBurst(trigger = uiState.matches.size, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Was sich mit den Treffern anfangen laesst: in den Plan, in die Sammlung, eine neue Runde.
 *
 * Jede Antwort der App steht fuer sich - was geklappt hat, was schon da war, was keinen
 * Platz fand und was sich nicht laden liess. Frueher stand hier ein Satz, der zwei ganz
 * verschiedene Gruende mit "oder" verband; damit liess sich nichts anfangen.
 */
@Composable
private fun MatchActions(uiState: MultiplayerUiState, actions: MultiplayerActions) {
    val stand = uiState.import
    val treffer = uiState.matches.size
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        // Der Hauptweg: die Treffer auf Plaetze des Wochenplans legen.
        val plan = stand.plan
        if (plan == null) {
            BouncyButton(
                onClick = actions.onAddToPlan,
                enabled = !stand.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(
                    if (stand.running == MatchAction.PLAN) {
                        stringResource(R.string.zuzweit_holt, stand.progress, treffer)
                    } else {
                        stringResource(R.string.zuzweit_in_plan)
                    },
                )
            }
        } else {
            PlanNotes(plan = plan, noRoom = stand.noRoom)
        }

        // Bis hierhin waren die Treffer nur Titel. Ein Griff bringt sie in die Sammlung -
        // die Einkaufsliste bleibt dabei, wie sie ist.
        if (stand.kept.size < treffer && !(stand.keepTried && stand.retry == MatchAction.KEEP)) {
            BouncyOutlinedButton(
                onClick = actions.onKeep,
                enabled = !stand.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(
                    if (stand.running == MatchAction.KEEP) {
                        stringResource(R.string.zuzweit_holt, stand.progress, treffer)
                    } else {
                        stringResource(R.string.mehrspieler_uebernehmen)
                    },
                )
            }
        }
        if (stand.keepTried) {
            ResultNote(pluralStringResource(R.plurals.zuzweit_behalten_ergebnis, stand.kept.size, stand.kept.size))
        }

        // Was nicht geklappt hat - mit dem Grund, und mit einem zweiten Anlauf.
        if (stand.hasProblems && !stand.isBusy) {
            if (stand.notLoaded > 0) {
                ResultNote(pluralStringResource(R.plurals.zuzweit_nicht_geladen, stand.notLoaded, stand.notLoaded))
            }
            if (stand.notYetShared > 0) {
                ResultNote(
                    pluralStringResource(R.plurals.zuzweit_noch_nicht_da, stand.notYetShared, stand.notYetShared),
                )
            }
            BouncyFilledTonalButton(
                onClick = actions.onRetryImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.zuzweit_erneut)) }
        }

        // Zutaten gibt es nur auf ausdruecklichen Wunsch - und nicht doppelt: Was im Plan
        // steht, hat seinen Einkauf schon auf der Liste.
        val posten = stand.listItems
        when {
            posten != null -> ResultNote(pluralStringResource(R.plurals.zuzweit_liste_ergebnis, posten, posten))

            stand.kept.isNotEmpty() && (plan == null || plan.placed == 0) -> BouncyOutlinedButton(
                onClick = actions.onListRequest,
                enabled = !stand.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.zuzweit_auf_liste)) }
        }

        BouncyTextButton(
            onClick = actions.onNewRound,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.zuzweit_neue_runde)) }
    }

    if (stand.askToReplace > 0) {
        AlertDialog(
            onDismissRequest = actions.onKeepPlanAsIs,
            title = { Text(stringResource(R.string.zuzweit_woche_voll_titel)) },
            text = {
                Text(pluralStringResource(R.plurals.zuzweit_woche_voll_text, stand.askToReplace, stand.askToReplace))
            },
            confirmButton = {
                BouncyTextButton(onClick = actions.onReplaceInPlan) {
                    Text(stringResource(R.string.zuzweit_woche_voll_ersetzen))
                }
            },
            dismissButton = {
                BouncyTextButton(onClick = actions.onKeepPlanAsIs) {
                    Text(stringResource(R.string.zuzweit_woche_voll_lassen))
                }
            },
        )
    }

    if (stand.askPortions) {
        PortionDialog(
            defaultPersons = uiState.own.persons,
            recipeServings = null,
            several = true,
            onConfirm = { persons, _ -> actions.onAddToShoppingList(persons) },
            onDismiss = actions.onListDismiss,
        )
    }
}

/** Was beim Einplanen herauskam - jede Zeile nur, wenn sie etwas zu sagen hat. */
@Composable
private fun PlanNotes(plan: PlanSummary, noRoom: Int) {
    if (plan.added > 0) ResultNote(pluralStringResource(R.plurals.zuzweit_plan_dazu, plan.added, plan.added))
    if (plan.replaced >
        0
    ) {
        ResultNote(pluralStringResource(R.plurals.zuzweit_plan_ersetzt, plan.replaced, plan.replaced))
    }
    if (plan.alreadyPlanned > 0) {
        ResultNote(pluralStringResource(R.plurals.zuzweit_plan_schon, plan.alreadyPlanned, plan.alreadyPlanned))
    }
    if (noRoom > 0) ResultNote(pluralStringResource(R.plurals.zuzweit_plan_kein_platz, noRoom, noRoom))
    if (plan.placed > 0) ResultNote(stringResource(R.string.zuzweit_plan_einkauf))
}

@Composable
private fun ResultNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Warum die Runde zu Ende ist - aus der Sicht der Person, die es liest. */
private fun RoundEnd.messageRes(isHost: Boolean): Int = when (this) {
    RoundEnd.ZIEL -> R.string.zuzweit_ende_ziel
    RoundEnd.LEER -> R.string.zuzweit_ende_leer
    RoundEnd.GASTGEBER -> if (isHost) R.string.zuzweit_ende_ich else R.string.zuzweit_ende_gegenueber
    RoundEnd.VERLASSEN -> if (isHost) R.string.zuzweit_ende_gegenueber else R.string.zuzweit_ende_ich
}

/** Ein gemeinsamer Treffer: Foto, Titel, Herz. */
@Composable
private fun MatchRow(match: SharedRecipe, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(Tokens.Radius.LgShape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeImage(
            photoUri = match.imageUrl,
            title = match.title,
            modifier = Modifier
                .size(56.dp)
                .clip(Tokens.Radius.MdShape),
        )
        Text(
            match.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MotionIcon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            motion = IconMotion.POP,
            playOnAppear = true,
            tint = RezeptliTheme.accents.tomato,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Warten - mit dem brodelnden Topf statt eines Kreisels. */
@Composable
private fun WaitingNote(title: String, message: String, busy: Boolean = true) {
    Column(
        modifier = Modifier
            .entrance(3)
            .fillMaxWidth()
            .cardSurface(Tokens.Radius.LgShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CookingPot(size = 64.dp, animated = busy)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
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
        BouncyTextButton(onClick = onDismiss) {
            Text(stringResource(R.string.action_ok))
        }
    }
}

private fun PairingError.messageRes(): Int = when (this) {
    PairingError.NO_CONNECTION -> R.string.mehrspieler_fehler_verbindung
    PairingError.UNKNOWN_CODE -> R.string.mehrspieler_fehler_code
    PairingError.EXPIRED -> R.string.mehrspieler_fehler_abgelaufen
    PairingError.FULL -> R.string.mehrspieler_fehler_voll
    PairingError.OUTDATED -> R.string.mehrspieler_fehler_veraltet
    PairingError.UNKNOWN -> R.string.mehrspieler_fehler_unbekannt
}

private const val BREATH_SCALE = 1.03f
private const val BREATH_MS = 1400

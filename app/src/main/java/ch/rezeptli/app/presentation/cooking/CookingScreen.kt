package ch.rezeptli.app.presentation.cooking

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.AmountFormatter
import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.timer.CookingTimer
import ch.rezeptli.app.domain.timer.TimerState
import ch.rezeptli.app.presentation.common.KeepScreenOn
import ch.rezeptli.app.presentation.common.ObserveAsEvents
import ch.rezeptli.app.presentation.common.components.AmountStepper
import ch.rezeptli.app.presentation.common.components.BouncyButton
import ch.rezeptli.app.presentation.common.components.BouncyFilledTonalButton
import ch.rezeptli.app.presentation.common.components.BouncyIconButton
import ch.rezeptli.app.presentation.common.components.BouncyOutlinedButton
import ch.rezeptli.app.presentation.common.components.BouncyTextButton
import ch.rezeptli.app.presentation.common.components.IconMotion
import ch.rezeptli.app.presentation.common.components.MotionIcon
import ch.rezeptli.app.presentation.common.components.RezeptliLoader
import ch.rezeptli.app.presentation.common.components.RezeptliProgressBar
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.label
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.Tokens
import ch.rezeptli.app.presentation.common.theme.cardSurface
import ch.rezeptli.app.presentation.common.theme.floatingPanel
import ch.rezeptli.app.presentation.common.theme.highlightRing
import kotlin.math.abs

/** Was sich am Timer tun laesst - gebuendelt, damit es nicht einzeln durch jede Ebene gereicht wird. */
class TimerActions(
    val onStart: () -> Unit = {},
    val onStop: () -> Unit = {},
    /** Eine Minute mehr oder weniger, waehrend er laeuft. */
    val onAdjust: (Int) -> Unit = {},
    /** Eine Minute mehr oder weniger, bevor er gestartet ist. */
    val onPlannedChange: (Int) -> Unit = {},
    /** Zum Schritt blaettern, zu dem der laufende Timer gehoert. */
    val onShowStep: () -> Unit = {},
)

/**
 * Der Kochmodus: ein Schritt pro Karte.
 *
 * Gewischt wird waagerecht - nach links geht es weiter, nach rechts zurueck. Dieselbe
 * Geste wie im Swipe-Modus, aber mit anderer Bedeutung; deshalb sieht der Bildschirm
 * bewusst anders aus, und es gibt zusaetzlich Knoepfe. Wer mit Mehl an den Fingern
 * kocht, trifft einen Knopf leichter als eine Geste.
 */
@Composable
fun CookingRoute(
    onClose: () -> Unit,
    viewModel: CookingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            CookingEvent.Finished -> onClose()
        }
    }

    // Beim Kochen liegt das Geraet auf der Arbeitsflaeche - der Bildschirm soll anbleiben.
    KeepScreenOn()

    // Solange ein Timer laeuft, schliesst auch die Zurueck-Taste nicht einfach: erst fragen.
    BackHandler(enabled = uiState.timer?.isFinished == false) { viewModel.onCloseRequest() }

    val askForNotifications = rememberNotificationRequest()
    val timerActions = remember(viewModel, askForNotifications) {
        TimerActions(
            onStart = {
                viewModel.onStartTimer()
                askForNotifications()
            },
            onStop = viewModel::onStopTimer,
            onAdjust = viewModel::onAdjustTimer,
            onPlannedChange = viewModel::onPlannedMinutesChange,
            onShowStep = viewModel::onShowTimerStep,
        )
    }

    CookingScreen(
        uiState = uiState,
        onClose = viewModel::onCloseRequest,
        onNextStep = viewModel::onNextStep,
        onPreviousStep = viewModel::onPreviousStep,
        timerActions = timerActions,
        onFinish = viewModel::onFinish,
    )

    if (uiState.pendingExit != null) {
        AlertDialog(
            onDismissRequest = viewModel::onExitDismiss,
            title = { Text(stringResource(R.string.kochmodus_timer_laeuft_titel)) },
            text = { Text(stringResource(R.string.kochmodus_timer_laeuft_text)) },
            confirmButton = {
                BouncyTextButton(onClick = viewModel::onExitConfirm) {
                    Text(stringResource(R.string.kochmodus_timer_laeuft_beenden))
                }
            },
            dismissButton = {
                BouncyTextButton(onClick = viewModel::onExitDismiss) {
                    Text(stringResource(R.string.kochmodus_timer_laeuft_bleiben))
                }
            },
        )
    }
}

/**
 * Fragt beim Starten eines Timers, ob die App Mitteilungen zeigen darf.
 *
 * Gebraucht wird das nur fuer die Anzeige, waehrend das Telefon gesperrt oder eine andere
 * App offen ist. Klingeln tut der Timer auch ohne - deshalb startet er sofort und wartet
 * nicht auf die Antwort. Wer zweimal abgelehnt hat, wird von Android nicht mehr gefragt.
 */
@Composable
private fun rememberNotificationRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    return remember(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingScreen(
    uiState: CookingUiState,
    onClose: () -> Unit,
    onNextStep: () -> Unit,
    onPreviousStep: () -> Unit,
    timerActions: TimerActions,
    onFinish: () -> Unit,
) {
    Scaffold(
        topBar = {
            RezeptliTopBar(
                title = uiState.recipe?.title.orEmpty(),
                titleStyle = MaterialTheme.typography.titleMedium,
                navigationIcon = {
                    BouncyIconButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.kochmodus_beenden),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.isLoading -> RezeptliLoader()

                !uiState.hasSteps -> Text(
                    text = stringResource(R.string.kochmodus_keine_schritte),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                )

                else -> CookingContent(
                    uiState = uiState,
                    onNextStep = onNextStep,
                    onPreviousStep = onPreviousStep,
                    timerActions = timerActions,
                    onFinish = onFinish,
                )
            }
        }
    }
}

@Composable
private fun CookingContent(
    uiState: CookingUiState,
    onNextStep: () -> Unit,
    onPreviousStep: () -> Unit,
    timerActions: TimerActions,
    onFinish: () -> Unit,
) {
    val swipeThreshold = LocalConfiguration.current.screenWidthDp / SWIPE_FRACTION

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.kochmodus_schritt,
                    uiState.stepNumber,
                    uiState.stepCount,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RezeptliProgressBar(progress = uiState.stepNumber.toFloat() / uiState.stepCount)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .pointerInput(uiState.stepIndex) {
                    var dragged = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // Nach links weiter, nach rechts zurueck - wie beim Blaettern.
                            if (abs(dragged) > swipeThreshold) {
                                if (dragged < 0) onNextStep() else onPreviousStep()
                            }
                            dragged = 0f
                        },
                    ) { _, amount -> dragged += amount }
                },
        ) {
            // Wie Umblaettern: Der naechste Schritt gleitet von rechts herein und federt ein.
            val motion = RezeptliTheme.motion
            AnimatedContent(
                targetState = uiState.stepIndex,
                transitionSpec = {
                    val forward = targetState > initialState
                    val width = if (forward) 1 else -1
                    if (motion.reduced) {
                        fadeIn(motion.fade()) togetherWith fadeOut(motion.fade())
                    } else {
                        (
                            slideInHorizontally(motion.offsetSpring()) { full -> width * full } +
                                fadeIn(tween(Tokens.Duration.Base))
                        ) togetherWith (
                            slideOutHorizontally(motion.offsetSpring()) { full -> -width * full } +
                                fadeOut(tween(Tokens.Duration.Fast))
                        )
                    }
                },
                label = "Schrittwechsel",
            ) { index ->
                val step = uiState.steps.getOrNull(index)
                if (step != null) {
                    StepCard(
                        text = step.text,
                        ingredients = uiState.ingredientsByStep[step.position].orEmpty(),
                        timer = uiState.timerForCurrentStep,
                        timerMinutes = uiState.minutesFor(step),
                        timerActions = timerActions,
                    )
                }
            }
        }

        // Gehoert der Timer zu einem anderen Schritt, bleibt er hier unten sichtbar.
        uiState.timerElsewhere?.let { timer ->
            TimerStrip(
                timer = timer,
                stepNumber = uiState.timerStepNumber,
                actions = timerActions,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BouncyOutlinedButton(
                onClick = onPreviousStep,
                enabled = uiState.hasPrevious,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.kochmodus_zurueck),
                )
            }
            Spacer(Modifier.weight(1f))
            if (uiState.isLastStep) {
                BouncyButton(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.kochmodus_fertig))
                }
            } else {
                BouncyButton(onClick = onNextStep, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.kochmodus_weiter))
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    text: String,
    ingredients: List<Ingredient>,
    timer: TimerState?,
    timerMinutes: Int?,
    timerActions: TimerActions,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .cardSurface(Tokens.Radius.XxlShape)
            .then(
                if (timer != null && !timer.isFinished) {
                    Modifier.highlightRing(Tokens.Radius.XxlShape, intensity = 0.6f)
                } else {
                    Modifier
                },
            ).verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.headlineSmall)

        if (ingredients.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.kochmodus_zutaten_fuer_schritt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ingredients.forEach { ingredient ->
                    Text(
                        text = ingredient.displayLine(),
                        style = RezeptliTheme.amountStyle,
                    )
                }
            }
        }

        if (timerMinutes != null) {
            TimerSection(timer = timer, minutes = timerMinutes, actions = timerActions)
        }
    }
}

/**
 * Der Timer eines Schritts: stellen, starten, nachstellen, stoppen.
 *
 * Die Minuten aus dem Rezept sind ein Vorschlag. Der eigene Herd, die eigene Pfanne und der
 * eigene Geschmack wissen es oft besser - deshalb laesst sich die Zeit vor dem Start und
 * auch waehrend sie laeuft um Minuten verschieben.
 */
@Composable
private fun TimerSection(
    timer: TimerState?,
    minutes: Int,
    actions: TimerActions,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .floatingPanel(Tokens.Radius.LgShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            timer == null -> {
                AmountStepper(
                    value = minutes,
                    caption = pluralStringResource(R.plurals.kochmodus_timer_minuten_wort, minutes),
                    range = 1..CookingTimer.MAX_MINUTES,
                    onChange = { actions.onPlannedChange(it - minutes) },
                )
                BouncyFilledTonalButton(
                    onClick = actions.onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    MotionIcon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        motion = IconMotion.WIGGLE,
                        playOnAppear = true,
                    )
                    Text(text = "  " + stringResource(R.string.kochmodus_timer_starten))
                }
            }

            timer.isFinished -> {
                // Abgelaufen: Die Uhr klingelt - sie wackelt, bis jemand reagiert.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RingingIcon()
                    Text(
                        text = stringResource(R.string.kochmodus_timer_abgelaufen),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                BouncyButton(
                    onClick = actions.onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.kochmodus_timer_stoppen))
                }
                BouncyOutlinedButton(
                    onClick = { actions.onAdjust(+1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.kochmodus_timer_nochmal))
                }
            }

            else -> {
                val gesprochen = timer.spoken()
                Text(
                    text = timer.remainingSeconds.asClock(),
                    style = RezeptliTheme.amountStyle.merge(MaterialTheme.typography.displaySmall),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = gesprochen },
                )
                RezeptliProgressBar(progress = timer.progress, color = RezeptliTheme.accents.butter)
                MinuteButtons(timer = timer, onAdjust = actions.onAdjust)
                BouncyOutlinedButton(
                    onClick = actions.onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.kochmodus_timer_stoppen))
                }
            }
        }
    }
}

/** "− 1 Min." und "+ 1 Min." nebeneinander - fuer den laufenden Timer. */
@Composable
private fun MinuteButtons(
    timer: TimerState,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weniger = stringResource(R.string.kochmodus_timer_weniger)
    val mehr = stringResource(R.string.kochmodus_timer_mehr)

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BouncyFilledTonalButton(
            onClick = { onAdjust(-1) },
            enabled = timer.canShorten,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics { contentDescription = weniger },
        ) {
            Text(stringResource(R.string.kochmodus_timer_weniger_kurz), maxLines = 1)
        }
        BouncyFilledTonalButton(
            onClick = { onAdjust(+1) },
            enabled = timer.canExtend,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics { contentDescription = mehr },
        ) {
            Text(stringResource(R.string.kochmodus_timer_mehr_kurz), maxLines = 1)
        }
    }
}

/**
 * Der Timer eines anderen Schritts, als schmaler Streifen ueber den Knoepfen.
 *
 * Antippen blaettert zu seinem Schritt. Klingelt er, laesst er sich gleich hier abstellen -
 * niemand soll mit nassen Haenden nach dem richtigen Schritt suchen muessen.
 */
@Composable
private fun TimerStrip(
    timer: TimerState,
    stepNumber: Int?,
    actions: TimerActions,
    modifier: Modifier = Modifier,
) {
    val nummer = stepNumber ?: (timer.stepPosition + 1)
    val text = if (timer.isFinished) {
        stringResource(R.string.kochmodus_timer_streifen_um, nummer)
    } else {
        stringResource(R.string.kochmodus_timer_streifen, nummer, timer.remainingSeconds.asClock())
    }
    val zeigen = stringResource(R.string.kochmodus_timer_zeigen)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .floatingPanel(Tokens.Radius.LgShape)
            .clickable(onClickLabel = zeigen, role = Role.Button, onClick = actions.onShowStep)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (timer.isFinished) {
            RingingIcon()
        } else {
            Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = text,
            style = RezeptliTheme.amountStyle.merge(MaterialTheme.typography.titleMedium),
            color = if (timer.isFinished) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        BouncyTextButton(onClick = actions.onStop, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.timer_meldung_stoppen))
        }
    }
}

/** Die Restzeit in Worten - fuer alle, die den Bildschirm vorgelesen bekommen. */
@Composable
private fun TimerState.spoken(): String =
    stringResource(R.string.kochmodus_timer_rest, remainingSeconds / 60, remainingSeconds % 60)

@Composable
private fun Ingredient.displayLine(): String {
    val amount = AmountFormatter.format(amount)
    val unitLabel = unit.label()
    return listOf(amount, unitLabel, name)
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

/**
 * Die klingelnde Uhr eines abgelaufenen Timers: wackelt in kurzen Schueben, solange sie
 * zu sehen ist. Bei reduzierter Bewegung steht sie still - die Farbe und der Text sagen
 * es dann.
 */
@Composable
private fun RingingIcon() {
    val motion = RezeptliTheme.motion
    val winkel = if (motion.reduced) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "klingeln")
        val wert by transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = RING_CYCLE_MS
                    0f at 0
                    -18f at 60
                    16f at 120
                    -12f at 180
                    10f at 240
                    0f at 300
                },
            ),
            label = "winkel",
        )
        wert
    }
    Icon(
        imageVector = Icons.Filled.Alarm,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.graphicsLayer { rotationZ = winkel },
    )
}

/** Sekunden als "12:05" - beim Kochen liest sich das schneller als eine Zahl. Ab einer Stunde "1:05:00". */
private fun Int.asClock(): String {
    val safe = coerceAtLeast(0)
    val hours = safe / SECONDS_PER_HOUR
    val minutes = safe % SECONDS_PER_HOUR / 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, safe % 60)
    } else {
        "%d:%02d".format(minutes, safe % 60)
    }
}

private const val SWIPE_FRACTION = 4
private const val SECONDS_PER_HOUR = 3600
private const val RING_CYCLE_MS = 1200

package ch.rezeptli.app.presentation.plan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Microwave
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.presentation.common.ObserveAsEvents
import ch.rezeptli.app.presentation.common.components.BouncyButton
import ch.rezeptli.app.presentation.common.components.BouncyOutlinedButton
import ch.rezeptli.app.presentation.common.components.IconMotion
import ch.rezeptli.app.presentation.common.components.RezeptliProgressBar
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.Tokens

/**
 * Die Einrichtung: erst willkommen heissen und erklaeren, dann zwoelf kurze Fragen, dann
 * baut Rezeptli den Plan.
 *
 * Jede Frage nimmt den ganzen Bildschirm ein und hat genau eine Aufgabe - so bleibt jede
 * Antwort ein Fingertipp. Nichts ist Pflicht, das Vorbelegte ist ein brauchbarer Plan.
 */
@Composable
fun PlanSetupRoute(
    onFinished: () -> Unit,
    viewModel: PlanSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            PlanSetupEvent.Finished -> onFinished()
        }
    }

    // Die Zurueck-Taste blaettert zurueck, statt die App mitten in der Einrichtung zu schliessen.
    BackHandler(enabled = !uiState.step.isFirst, onBack = viewModel::onBack)

    PlanSetupScreen(uiState = uiState, viewModel = viewModel)
}

@Composable
private fun PlanSetupScreen(uiState: PlanSetupUiState, viewModel: PlanSetupViewModel) {
    val step = uiState.step

    Scaffold(
        topBar = {
            // Begruessung und Ueberblick sind keine Schritte - dort stuende "Schritt 0 von 12".
            if (!step.isIntro) {
                RezeptliTopBar(
                    title = stringResource(R.string.setup_schritt, uiState.stepNumber, uiState.stepCount),
                    titleStyle = MaterialTheme.typography.labelLarge,
                    onBack = viewModel::onBack,
                )
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!step.isFirst) {
                    BouncyOutlinedButton(
                        onClick = viewModel::onBack,
                        shape = Tokens.Radius.PillShape,
                        modifier = Modifier.heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.setup_zurueck)) }
                }
                BouncyButton(
                    onClick = viewModel::onNext,
                    enabled = !uiState.isSaving,
                    shape = Tokens.Radius.PillShape,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) {
                    Text(
                        text = stringResource(
                            when {
                                step == SetupStep.WILLKOMMEN -> R.string.willkommen_los
                                step == SetupStep.UEBERBLICK -> R.string.ueberblick_einrichten
                                step.isLast -> R.string.setup_plan_erstellen
                                else -> R.string.setup_weiter
                            },
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!step.isIntro) {
                RezeptliProgressBar(
                    progress = uiState.progress,
                    height = 8.dp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            // Vorwaerts gleitet die naechste Seite von rechts herein und federt ein,
            // zurueck von links. Bei reduzierter Bewegung blenden die Seiten nur ueber.
            val motion = RezeptliTheme.motion
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val direction = if (forward) 1 else -1
                    if (motion.reduced) {
                        fadeIn(motion.fade()) togetherWith fadeOut(motion.fade())
                    } else {
                        (
                            slideInHorizontally(motion.offsetSpring()) {
                                it / 3 * direction
                            } + fadeIn(motion.fade())
                        ).togetherWith(
                            slideOutHorizontally(motion.offsetSpring()) {
                                -it / 3 * direction
                            } + fadeOut(tween(Tokens.Duration.Fast)),
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                label = "Einrichtungsseite",
            ) { shown ->
                when (shown) {
                    SetupStep.WILLKOMMEN -> WelcomePage()
                    SetupStep.UEBERBLICK -> OverviewPage()
                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Question(step = shown, uiState = uiState, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

/** Eine Frage der Einrichtung: ihr Symbol, Titel und Erklaerung - darunter die Antwortflaeche. */
@Composable
private fun Question(step: SetupStep, uiState: PlanSetupUiState, viewModel: PlanSetupViewModel) {
    val profile = uiState.profile

    when (step) {
        // Die beiden Seiten davor fragen nichts.
        SetupStep.WILLKOMMEN, SetupStep.UEBERBLICK -> Unit

        SetupStep.NAME -> StepFrame(step, R.string.setup_name_titel, R.string.setup_name_text) {
            NameField(name = profile.firstName, onChange = viewModel::onNameChange)
        }

        SetupStep.DESIGN -> StepFrame(step, R.string.setup_design_titel, R.string.setup_design_text) {
            ThemePicker(selected = uiState.theme, onSelect = viewModel::onThemeSelect)
        }

        SetupStep.LAND -> StepFrame(step, R.string.onboarding_land_frage, R.string.vorlieben_land_text) {
            CountryPicker(selected = profile.country, onSelect = viewModel::onCountrySelect)
        }

        SetupStep.KUECHEN -> StepFrame(step, R.string.onboarding_kuechen_titel, R.string.vorlieben_kuechen_text) {
            CuisinePicker(selected = profile.cuisines, onToggle = viewModel::onCuisineToggle)
        }

        SetupStep.ERNAEHRUNG -> StepFrame(step, R.string.setup_ernaehrung_titel, R.string.setup_ernaehrung_text) {
            DietPicker(
                profile = profile,
                onMainDiet = viewModel::onMainDiet,
                onToggleDiet = viewModel::onDietToggle,
            )
            Text(
                text = stringResource(R.string.setup_unvertraeglich_titel),
                style = MaterialTheme.typography.titleLarge,
            )
            IntolerancePicker(
                selected = profile.intolerances,
                onToggle = viewModel::onIntoleranceToggle,
            )
        }

        SetupStep.ABNEIGUNGEN -> StepFrame(step, R.string.setup_abneigung_titel, R.string.setup_abneigung_text) {
            DislikePicker(
                selected = profile.dislikes,
                custom = profile.customDislikes,
                onToggle = viewModel::onDislikeToggle,
                onAddCustom = viewModel::onCustomDislikeAdd,
                onRemoveCustom = viewModel::onCustomDislikeRemove,
            )
        }

        SetupStep.PERSONEN -> StepFrame(step, R.string.setup_personen_titel, R.string.setup_personen_text) {
            PersonStepper(persons = profile.persons, onChange = viewModel::onPersonsChange)
        }

        SetupStep.TAGE -> StepFrame(step, R.string.setup_tage_titel, R.string.setup_tage_text) {
            DayPicker(selected = profile.days.toSet(), onToggle = viewModel::onDayToggle)
            MealSlotPicker(profile = profile, onToggle = viewModel::onSlotToggle)
        }

        SetupStep.LADEN -> StepFrame(step, R.string.setup_laden_titel, R.string.setup_laden_text) {
            StoreGrid(
                market = profile.market,
                selected = profile.stores,
                onToggle = viewModel::onStoreToggle,
                onMakeMain = viewModel::onMainStore,
            )
        }

        SetupStep.BUDGET -> StepFrame(step, R.string.setup_budget_titel, R.string.setup_budget_text) {
            BudgetSlider(
                budget = profile.budget,
                currency = profile.market.currency,
                persons = profile.persons,
                meals = profile.mealsPerWeek,
                onChange = viewModel::onBudgetChange,
            )
        }

        SetupStep.STIMMUNG -> StepFrame(step, R.string.setup_stimmung_titel, R.string.setup_stimmung_text) {
            MoodPicker(selected = profile.moods, onToggle = viewModel::onMoodToggle)
        }

        SetupStep.GERAETE -> StepFrame(step, R.string.setup_geraete_titel, R.string.setup_geraete_text) {
            AppliancePicker(selected = profile.appliances, onToggle = viewModel::onApplianceToggle)
        }
    }
}

/**
 * Der Kopf einer Frage: ihr Symbol, das beim Erscheinen kurz auftritt, dann Titel und
 * Erklaerung. Darunter die Antwortflaeche.
 */
@Composable
private fun StepFrame(
    step: SetupStep,
    titleRes: Int,
    textRes: Int,
    content: @Composable () -> Unit,
) {
    val farben = RezeptliTheme.tags.all
    val (icon, motion) = step.symbol()

    IconBadge(
        icon = icon,
        motion = motion,
        colors = farben[step.ordinal % farben.size],
        size = 64.dp,
        playOnAppear = true,
    )
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    content()
}

/** Das Symbol einer Frage und wie es sich bewegt - die Weltkugel dreht sich, der Kalender blaettert. */
private fun SetupStep.symbol(): Pair<ImageVector, IconMotion> = when (this) {
    SetupStep.WILLKOMMEN, SetupStep.UEBERBLICK, SetupStep.NAME -> Icons.Filled.Face to IconMotion.POP
    SetupStep.DESIGN -> Icons.Filled.Palette to IconMotion.SPIN
    SetupStep.LAND -> Icons.Filled.Public to IconMotion.SPIN
    SetupStep.KUECHEN -> Icons.Filled.RestaurantMenu to IconMotion.WIGGLE
    SetupStep.ERNAEHRUNG -> Icons.Filled.Eco to IconMotion.HOP
    SetupStep.ABNEIGUNGEN -> Icons.Filled.SentimentDissatisfied to IconMotion.WIGGLE
    SetupStep.PERSONEN -> Icons.Filled.Groups to IconMotion.POP
    SetupStep.TAGE -> Icons.Filled.CalendarMonth to IconMotion.FLIP
    SetupStep.LADEN -> Icons.Filled.Storefront to IconMotion.HOP
    SetupStep.BUDGET -> Icons.Filled.Savings to IconMotion.HOP
    SetupStep.STIMMUNG -> Icons.Filled.EmojiEmotions to IconMotion.WIGGLE
    SetupStep.GERAETE -> Icons.Filled.Microwave to IconMotion.POP
}

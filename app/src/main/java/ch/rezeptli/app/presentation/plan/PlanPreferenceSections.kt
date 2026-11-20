package ch.rezeptli.app.presentation.plan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.Currency
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.Market
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Mood
import ch.rezeptli.app.domain.profile.ProfileEdits
import ch.rezeptli.app.domain.profile.StoreChain
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.profile.mainDiet
import ch.rezeptli.app.domain.settings.ThemeChoice
import ch.rezeptli.app.presentation.common.components.BouncyFilledIconButton
import ch.rezeptli.app.presentation.common.components.ChoiceChipGroup
import ch.rezeptli.app.presentation.common.components.IconMotion
import ch.rezeptli.app.presentation.common.components.MotionIcon
import ch.rezeptli.app.presentation.common.components.popOnChange
import ch.rezeptli.app.presentation.common.components.pressScale
import ch.rezeptli.app.presentation.common.formatMoney
import ch.rezeptli.app.presentation.common.label
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.Tokens
import ch.rezeptli.app.presentation.common.theme.supportsMaterialYou

/*
 * Die Bausteine, aus denen Einrichtung und Vorlieben bestehen.
 *
 * Beide Bildschirme fragen dasselbe - einmal gefuehrt, einmal als Liste. Damit sie sich
 * nie auseinanderentwickeln, gibt es jede Frage nur einmal.
 */

/**
 * Eine antippbare Kachel mit Auswahlzustand: Fuellung und Rand zeigen, ob sie gewaehlt
 * ist. Sie gibt unter dem Finger nach, und beim Umschalten springt sie kurz auf.
 */
@Composable
private fun SelectableTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = Tokens.Radius.XlShape
    val motion = RezeptliTheme.motion
    val accent = MaterialTheme.colorScheme.primary
    val fuellung by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else RezeptliTheme.accents.cardFill,
        animationSpec = motion.fade(),
        label = "kachel",
    )
    val rand by animateColorAsState(
        targetValue = if (selected) accent else RezeptliTheme.accents.hairline,
        animationSpec = motion.fade(),
        label = "kachel-rand",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction)
            .popOnChange(selected)
            .clip(shape)
            .background(fuellung)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = rand,
                shape = shape,
            ).clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Checkbox,
                onClick = onClick,
            ).semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Der Vorname - freiwillig, er steht nur in der Begruessung. */
@Composable
fun NameField(
    name: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.onboarding_vorname)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun CountryPicker(
    selected: Country?,
    onSelect: (Country) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChoiceChipGroup(
        options = Country.entries,
        selected = setOfNotNull(selected),
        label = { it.label() },
        onToggle = onSelect,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun CuisinePicker(
    selected: Set<Cuisine>,
    onToggle: (Cuisine) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChoiceChipGroup(
        options = Cuisine.entries,
        selected = selected,
        label = { it.label() },
        onToggle = onToggle,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Die Laeden des Landes - bis zu drei, in der Reihenfolge der Auswahl.
 *
 * Die Zahl auf der Kachel zeigt den Rang: 1 ist der Hauptladen, mit dem gerechnet wird.
 * Ein Tipp auf einen schon gewaehlten Laden waehlt ihn ab; wer den Hauptladen wechseln
 * will, macht einen anderen mit [onMakeMain] dazu.
 */
@Composable
fun StoreGrid(
    market: Market,
    selected: List<StoreChain>,
    onToggle: (StoreChain) -> Unit,
    onMakeMain: (StoreChain) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        market.stores.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { chain ->
                    val rang = selected.indexOf(chain)
                    SelectableTile(
                        selected = rang >= 0,
                        onClick = { onToggle(chain) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.9f),
                    ) {
                        Text(
                            text = chain.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        if (rang >= 0) {
                            Text(
                                text = (rang + 1).toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(22.dp)
                                    .clip(Tokens.Radius.CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                // Eine ungerade Kachelzahl soll die letzte nicht auf volle Breite ziehen.
                if (row.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
        val haupt = selected.firstOrNull()
        Text(
            text = when {
                haupt == null -> stringResource(R.string.laeden_hinweis_leer, UserProfile.MAX_STORES)
                selected.size == 1 -> stringResource(R.string.laeden_hinweis_einer, haupt.label)
                else -> stringResource(R.string.laeden_hinweis_mehrere, haupt.label)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Den Hauptladen wechseln, ohne alles abzuwaehlen.
        if (selected.size > 1) {
            ChoiceChipGroup(
                options = selected,
                selected = setOfNotNull(haupt),
                label = { stringResource(R.string.laeden_als_hauptladen, it.label) },
                onToggle = onMakeMain,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun PersonStepper(
    persons: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BouncyFilledIconButton(onClick = { onChange(-1) }, enabled = persons > 1, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.setup_personen_weniger))
        }
        Column(
            modifier = Modifier.heightIn(min = 96.dp).fillMaxWidth(0.5f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Die Zahl rollt nach oben oder unten, je nachdem, ob es mehr oder weniger werden.
            val motion = RezeptliTheme.motion
            AnimatedContent(
                targetState = persons,
                transitionSpec = {
                    if (motion.reduced) {
                        fadeIn(motion.fade()) togetherWith fadeOut(motion.fade())
                    } else {
                        val hoch = targetState > initialState
                        (slideInVertically(motion.offsetSpring()) { if (hoch) it else -it } + fadeIn(motion.fade()))
                            .togetherWith(
                                slideOutVertically(motion.offsetSpring()) { if (hoch) -it else it } +
                                    fadeOut(motion.fade()),
                            )
                    }
                },
                label = "personen",
            ) { anzahl ->
                Text(
                    text = anzahl.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = if (persons == 1) {
                    stringResource(R.string.setup_personen_eine)
                } else {
                    stringResource(R.string.setup_personen_anzahl, persons)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BouncyFilledIconButton(
            onClick = { onChange(1) },
            enabled = persons < UserProfile.MAX_HOUSEHOLD_SIZE,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.setup_personen_mehr))
        }
    }
}

@Composable
fun DayPicker(
    selected: Set<Weekday>,
    onToggle: (Weekday) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Weekday.entries.forEach { day ->
                DayPill(
                    label = day.short,
                    selected = day in selected,
                    onClick = { onToggle(day) },
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.72f),
                )
            }
        }
    }
}

/**
 * Mittagessen, Abendessen oder beides.
 *
 * Darunter steht, was das fuer die Woche heisst - die Zahl der Mahlzeiten ist das, was
 * am Ende im Plan steht und eingekauft wird.
 */
@Composable
fun MealSlotPicker(
    profile: UserProfile,
    onToggle: (MealSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MealSlot.entries.forEach { slot ->
                SelectableTile(
                    selected = slot in profile.slots,
                    onClick = { onToggle(slot) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp),
                ) {
                    Text(
                        text = slot.label,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.setup_tage_anzahl, profile.mealsPerWeek),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BudgetSlider(
    budget: Int,
    currency: Currency,
    persons: Int,
    meals: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = UserProfile.MIN_BUDGET..UserProfile.MAX_BUDGET
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = formatMoney(budget, currency),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Slider(
            value = budget.toFloat(),
            onValueChange = { onChange((it / BUDGET_STEP).toInt() * BUDGET_STEP) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / BUDGET_STEP - 1,
        )
        val perMeal = budget.toDouble() / (persons.coerceAtLeast(1) * meals.coerceAtLeast(1))
        Text(
            text = stringResource(R.string.setup_budget_pro_mahlzeit, formatMoney(perMeal, currency)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Die Stimmungen als Liste - jede in ihrer eigenen Zeile, immer am selben Ort.
 *
 * Vorher waren es Chips in einer umbrechenden Reihe; beim Auswaehlen verschob sich, was
 * wo stand. Eine Liste bleibt stehen, und "hoechstens drei" laesst sich daran ablesen:
 * Ist die Grenze erreicht, werden die uebrigen Zeilen blass und lassen sich nicht waehlen.
 */
@Composable
fun MoodPicker(
    selected: Set<Mood>,
    onToggle: (Mood) -> Unit,
    modifier: Modifier = Modifier,
) {
    val voll = selected.size >= UserProfile.MAX_MOODS
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Mood.entries.forEach { mood ->
            val gewaehlt = mood in selected
            val waehlbar = gewaehlt || !voll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(Tokens.Radius.MdShape)
                    .toggleable(
                        value = gewaehlt,
                        enabled = waehlbar,
                        role = Role.Checkbox,
                        onValueChange = { onToggle(mood) },
                    ).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Die ganze Zeile schaltet - das Kaestchen ist nur die Anzeige.
                Checkbox(checked = gewaehlt, onCheckedChange = null, enabled = waehlbar)
                Text(
                    text = mood.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (waehlbar) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = stringResource(R.string.setup_stimmung_zaehler, selected.size, UserProfile.MAX_MOODS),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Die Ernaehrungsform: eine der Hauptformen oder keine, dazu "kohlenhydratarm". */
@Composable
fun DietPicker(
    profile: UserProfile,
    onMainDiet: (Diet?) -> Unit,
    onToggleDiet: (Diet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val options = listOf<Diet?>(null, Diet.VEGETARISCH, Diet.VEGAN, Diet.PESCETARISCH)
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { diet ->
                    SelectableTile(
                        selected = profile.mainDiet == diet,
                        onClick = { onMainDiet(diet) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 72.dp),
                    ) {
                        Text(
                            text = diet?.label() ?: stringResource(R.string.setup_ernaehrung_keine),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        // Dieselbe Kachel wie oben, aber ueber die ganze Breite: "Wenig Kohlenhydrate" ist
        // keine fuenfte Ernaehrungsform, sondern laesst sich mit jeder kombinieren.
        SelectableTile(
            selected = Diet.KOHLENHYDRATARM in profile.diets,
            onClick = { onToggleDiet(Diet.KOHLENHYDRATARM) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = Diet.KOHLENHYDRATARM.label(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.setup_ernaehrung_zusaetzlich),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Unvertraeglichkeiten - mit dem Hinweis, was die App dabei nicht leisten kann. */
@Composable
fun IntolerancePicker(
    selected: Set<Intolerance>,
    onToggle: (Intolerance) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceChipGroup(
            options = Intolerance.entries,
            selected = selected,
            label = { it.label() },
            onToggle = onToggle,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.setup_unvertraeglich_hinweis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Was jemand nicht mag: eine Liste gaengiger Abneigungen und darunter eigene Stichworte
 * fuer alles, was fehlt.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DislikePicker(
    selected: Set<Dislike>,
    custom: List<String>,
    onToggle: (Dislike) -> Unit,
    onAddCustom: (String) -> Unit,
    onRemoveCustom: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var eingabe by rememberSaveable { mutableStateOf("") }
    val hinzufuegen = {
        onAddCustom(eingabe)
        eingabe = ""
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceChipGroup(
            options = Dislike.entries,
            selected = selected,
            label = { it.label() },
            onToggle = onToggle,
            modifier = Modifier.fillMaxWidth(),
        )
        if (custom.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                custom.forEach { wort ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveCustom(wort) },
                        label = { Text(wort) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.abneigung_entfernen, wort),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = eingabe,
                onValueChange = { eingabe = it.take(UserProfile.MAX_DISLIKE_LENGTH) },
                label = { Text(stringResource(R.string.abneigung_eigene)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { hinzufuegen() }),
                modifier = Modifier.weight(1f),
            )
            BouncyFilledIconButton(
                onClick = hinzufuegen,
                enabled = eingabe.trim().length >= ProfileEdits.MIN_DISLIKE_LENGTH &&
                    custom.size < UserProfile.MAX_CUSTOM_DISLIKES,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.abneigung_hinzufuegen))
            }
        }
        Text(
            text = stringResource(R.string.abneigung_hinweis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AppliancePicker(
    selected: Set<Appliance>,
    onToggle: (Appliance) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Appliance.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { appliance ->
                    SelectableTile(
                        selected = appliance in selected,
                        onClick = { onToggle(appliance) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 88.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = appliance.emoji(), style = MaterialTheme.typography.headlineMedium)
                            Text(
                                text = appliance.label,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                if (row.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun Appliance.emoji(): String = when (this) {
    Appliance.BACKOFEN -> "🔥"
    Appliance.HERD -> "🍳"
    Appliance.MIKROWELLE -> "⚡"
    Appliance.HEISSLUFT -> "🌬️"
    Appliance.DAMPFGARER -> "♨️"
    Appliance.SLOWCOOKER -> "🍲"
    Appliance.REISKOCHER -> "🍚"
}

/**
 * Das Aussehen der App: wie das Geraet, hell, dunkel - oder in den Farben des
 * Hintergrundbilds.
 *
 * Material You steht nur zur Wahl, wo das Geraet es kennt (ab Android 12). Die Wahl gilt
 * sofort: Wer hier tippt, sieht die App in derselben Sekunde umschalten.
 */
@Composable
fun ThemePicker(
    selected: ThemeChoice,
    onSelect: (ThemeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = ThemeChoice.entries.filter { it != ThemeChoice.MATERIAL_YOU || supportsMaterialYou }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { choice ->
                    val gewaehlt = selected == choice
                    SelectableTile(
                        selected = gewaehlt,
                        onClick = { onSelect(choice) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 88.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MotionIcon(
                                imageVector = choice.icon(),
                                contentDescription = null,
                                motion = IconMotion.POP,
                                trigger = if (gewaehlt) true else null,
                                tint = if (gewaehlt) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = choice.label(),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                // Eine einzelne Kachel in der letzten Reihe bleibt so breit wie die anderen.
                if (row.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
        if (supportsMaterialYou) {
            Text(
                text = stringResource(R.string.design_material_you_hinweis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ThemeChoice.icon(): ImageVector = when (this) {
    ThemeChoice.SYSTEM -> Icons.Outlined.BrightnessAuto
    ThemeChoice.HELL -> Icons.Outlined.LightMode
    ThemeChoice.DUNKEL -> Icons.Outlined.DarkMode
    ThemeChoice.MATERIAL_YOU -> Icons.Outlined.Palette
}

/**
 * Der Schalter fuer weniger Bewegung.
 *
 * Hat Android "Animationen entfernen" eingeschaltet, ist Rezeptli ohnehin ruhig - dann
 * steht der Schalter auf an und laesst sich nicht ausschalten. Die Android-Einstellung
 * ist eine Bedienungshilfe; eine App sollte sie nicht uebergehen.
 */
@Composable
fun MotionSetting(
    reduceMotion: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemReduced = RezeptliTheme.motion.reduced && !reduceMotion
    val checked = reduceMotion || systemReduced

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                enabled = !systemReduced,
                role = Role.Switch,
                onValueChange = onChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MotionIcon(
            imageVector = Icons.Outlined.SlowMotionVideo,
            contentDescription = null,
            motion = IconMotion.WIGGLE,
            trigger = checked,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.einstellungen_bewegung),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (systemReduced) R.string.einstellungen_bewegung_system else R.string.einstellungen_bewegung_text,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Die ganze Zeile schaltet - der Schalter selbst ist nur die Anzeige.
        Switch(checked = checked, onCheckedChange = null, enabled = !systemReduced)
    }
}

private const val BUDGET_STEP = 10
private const val DISABLED_ALPHA = 0.38f

/**
 * Ein Wochentag als Pille: gewaehlt voll gruen, sonst Papier. Springt beim Umschalten.
 *
 * [description] ist, was vorgelesen wird - "Montag" statt des gezeigten "Mo".
 */
@Composable
fun DayPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val motion = RezeptliTheme.motion
    val fuellung by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else RezeptliTheme.accents.cardFill,
        animationSpec = motion.fade(),
        label = "tag",
    )
    val schrift by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = motion.fade(),
        label = "tag-schrift",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction)
            .popOnChange(selected)
            .clip(Tokens.Radius.PillShape)
            .background(fuellung)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else RezeptliTheme.accents.hairline,
                Tokens.Radius.PillShape,
            ).clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Checkbox,
                onClick = onClick,
            ).semantics {
                this.selected = selected
                if (description != null) contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = schrift,
            maxLines = 1,
            // Mit einer Beschreibung wird die vorgelesen - das Kuerzel dazu waere doppelt.
            modifier = if (description != null) Modifier.clearAndSetSemantics {} else Modifier,
        )
    }
}

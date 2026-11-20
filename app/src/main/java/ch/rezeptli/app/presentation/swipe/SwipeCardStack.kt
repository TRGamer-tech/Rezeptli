package ch.rezeptli.app.presentation.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.presentation.common.components.RecipeImage
import ch.rezeptli.app.presentation.common.theme.MotionScheme
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.Tokens
import ch.rezeptli.app.presentation.common.theme.cardSurface
import ch.rezeptli.app.presentation.common.theme.highlightRing
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Erlaubt es, die oberste Karte auch ohne Wischgeste wegzuschicken - etwa ueber die
 * Ja/Nein-Schaltflaechen oder einen Screenreader.
 */
@Stable
class SwipeCardStackState {
    internal var requestedSwipe by mutableStateOf<Boolean?>(null)
        private set

    fun swipe(liked: Boolean) {
        requestedSwipe = liked
    }

    internal fun consumeRequest() {
        requestedSwipe = null
    }
}

@Composable
fun rememberSwipeCardStackState(): SwipeCardStackState = remember { SwipeCardStackState() }

/**
 * Kartenstapel des Swipe-Modus.
 *
 * Nur die oberste Karte reagiert auf Gesten; die dahinter sind leicht verkleinert und
 * angehoben, damit sichtbar ist, dass es weitergeht. Fliegt die oberste Karte weg,
 * federt die naechste nach vorne. Waehrend des Wischens faerbt sich der Rand gruen oder
 * rot, und ein Stempel "Ja"/"Nein" waechst mit der Geste - die Entscheidung ist sichtbar,
 * bevor man loslaesst.
 *
 * Mit [onInfo] laesst sich die oberste Karte antippen: Dann zeigt der Aufrufer, woher das
 * Rezept kommt und was hineingehoert. Ein kleines Info-Zeichen in der Ecke verraet, dass
 * das geht.
 */
@Composable
fun SwipeCardStack(
    cards: List<RecipeSummary>,
    onSwiped: (RecipeSummary, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    state: SwipeCardStackState = rememberSwipeCardStackState(),
    onInfo: ((RecipeSummary) -> Unit)? = null,
) {
    // Nur die obersten Karten zeichnen. Wer den ganzen Stapel hereingibt, bekam
    // sonst jede Karte gezeichnet - bei dreissig Karten ein Faecher aus dreissig
    // halbdurchsichtigen Flaechen quer ueber den Bildschirm. Das gehoert hierher
    // und nicht in jeden Aufrufer: Die Karte weiss selbst, wie tief ihr Stapel ist.
    val sichtbar = cards.take(VISIBLE_CARDS)

    Box(modifier = modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        // Von hinten nach vorne zeichnen, damit die oberste Karte oben liegt. Der
        // Schluessel haelt jede Karte fest, waehrend sie im Stapel nach vorne rueckt -
        // so kann sie dorthin gleiten, statt zu springen.
        sichtbar.asReversed().forEachIndexed { reversedIndex, card ->
            val indexFromTop = sichtbar.size - 1 - reversedIndex
            key(card.id) {
                if (indexFromTop == 0) {
                    TopSwipeCard(
                        card = card,
                        state = state,
                        onSwiped = { liked -> onSwiped(card, liked) },
                        onInfo = onInfo?.let { zeigen -> { zeigen(card) } },
                    )
                } else {
                    BackgroundCard(card = card, indexFromTop = indexFromTop)
                }
            }
        }
    }
}

@Composable
private fun TopSwipeCard(
    card: RecipeSummary,
    state: SwipeCardStackState,
    onSwiped: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onInfo: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val motion = RezeptliTheme.motion
    val offsetX = remember(card.id) { Animatable(0f) }
    val fade = remember(card.id) { Animatable(1f) }
    // Die Karte kommt aus der zweiten Reihe nach vorne - sie startet so klein, wie sie dort war.
    val lift = remember(card.id) { Animatable(if (motion.reduced) 1f else 1f - BACKGROUND_SCALE_STEP) }
    var cardWidth by remember { mutableStateOf(1f) }

    LaunchedEffect(card.id) { lift.animateTo(1f, motion.bouncy()) }

    val likeLabel = stringResource(R.string.swipe_yes)
    val dislikeLabel = stringResource(R.string.swipe_no)
    val infoLabel = stringResource(R.string.karte_mehr)
    val cardDescription = stringResource(R.string.swipe_card_description, card.title)
    val tap = remember { MutableInteractionSource() }

    /**
     * Schickt die Karte weg: Sie fliegt zur Seite hinaus - oder blendet bei reduzierter
     * Bewegung nur kurz aus.
     */
    suspend fun dismiss(liked: Boolean, width: Float) {
        if (motion.reduced) {
            fade.animateTo(0f, tween(Tokens.Duration.Instant))
        } else {
            offsetX.animateTo(
                targetValue = if (liked) width * DISMISS_FACTOR else -width * DISMISS_FACTOR,
                animationSpec = tween(DISMISS_DURATION_MS, easing = Tokens.Ease.Exit),
            )
        }
        onSwiped(liked)
    }

    // Ja/Nein ueber die Schaltflaechen oder den Screenreader: dieselbe Animation wie beim Wischen.
    //
    // Die Anfrage wird ueber einen snapshotFlow beobachtet und nicht ueber einen
    // LaunchedEffect-Key: Das Zuruecksetzen der Anfrage wuerde sonst den Effekt neu
    // starten und die laufende Animation abbrechen, bevor onSwiped ueberhaupt ausgeloest ist.
    LaunchedEffect(card.id) {
        snapshotFlow { state.requestedSwipe }
            .filterNotNull()
            .collect { liked ->
                state.consumeRequest()
                if (!motion.reduced) {
                    // Ein kurzes Ausholen in die Richtung, damit der Stempel sichtbar wird.
                    offsetX.animateTo(
                        if (liked) cardWidth * SWIPE_THRESHOLD_FACTOR else -cardWidth * SWIPE_THRESHOLD_FACTOR,
                        motion.snappy(),
                    )
                }
                dismiss(liked, cardWidth)
            }
    }

    val progress = (offsetX.value / (cardWidth * SWIPE_THRESHOLD_FACTOR)).coerceIn(-1f, 1f)

    SwipeCardContent(
        card = card,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size -> cardWidth = size.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationX = offsetX.value
                rotationZ = (offsetX.value / ROTATION_DIVISOR).coerceIn(-MAX_ROTATION, MAX_ROTATION)
                scaleX = lift.value
                scaleY = lift.value
                alpha = fade.value
            }.then(
                // Ein Tipp ohne Bewegung zeigt mehr zum Rezept; sobald sich der Finger
                // bewegt, uebernimmt die Wischgeste. Ohne Welle - die Karte ist ein Foto.
                if (onInfo == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = tap,
                        indication = null,
                        onClickLabel = infoLabel,
                        role = Role.Button,
                        onClick = onInfo,
                    )
                },
            ).pointerInput(card.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val threshold = size.width * SWIPE_THRESHOLD_FACTOR
                            when {
                                offsetX.value > threshold -> dismiss(true, size.width.toFloat())
                                offsetX.value < -threshold -> dismiss(false, size.width.toFloat())
                                // Nicht weit genug: federt zurueck in die Mitte.
                                else -> offsetX.animateTo(0f, returnSpec(motion))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, returnSpec(motion)) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    },
                )
            }.semantics {
                contentDescription = cardDescription
                customActions = listOf(
                    CustomAccessibilityAction(likeLabel) {
                        onSwiped(true)
                        true
                    },
                    CustomAccessibilityAction(dislikeLabel) {
                        onSwiped(false)
                        true
                    },
                )
            },
        decisionProgress = progress,
        showsInfo = onInfo != null,
    )
}

/** Zurueck in die Mitte: mit Schwung, oder bei reduzierter Bewegung ohne Nachschwingen. */
private fun returnSpec(motion: MotionScheme): FiniteAnimationSpec<Float> = if (motion.reduced) {
    tween(Tokens.Duration.Fast)
} else {
    motion.bouncy()
}

@Composable
private fun BackgroundCard(
    card: RecipeSummary,
    indexFromTop: Int,
    modifier: Modifier = Modifier,
) {
    val motion = RezeptliTheme.motion
    // Rueckt eine Karte nach vorne, gleitet sie an ihren neuen Platz.
    val scale by animateFloatAsState(
        targetValue = 1f - indexFromTop * BACKGROUND_SCALE_STEP,
        animationSpec = motion.bouncy(),
        label = "stapel-groesse",
    )
    val lift by animateFloatAsState(
        targetValue = indexFromTop * BACKGROUND_OFFSET_DP,
        animationSpec = motion.bouncy(),
        label = "stapel-hoehe",
    )
    SwipeCardContent(
        card = card,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = -lift * density
            },
        decisionProgress = 0f,
        isInteractive = false,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwipeCardContent(
    card: RecipeSummary,
    decisionProgress: Float,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    showsInfo: Boolean = false,
) {
    val accents = RezeptliTheme.accents
    val ringColor = when {
        decisionProgress > 0f -> accents.swipeYes
        decisionProgress < 0f -> accents.swipeNo
        else -> accents.hairline
    }

    // Das Foto traegt die Karte; Titel und Angaben stehen direkt darauf, ueber einem
    // weichen Schatten von unten. Der Rand folgt der Geste und faerbt sich gruen oder rot.
    Box(
        modifier = modifier
            .cardSurface(Tokens.Radius.XxlShape, elevation = if (isInteractive) 8.dp else 2.dp)
            .highlightRing(
                shape = Tokens.Radius.XxlShape,
                intensity = if (isInteractive) abs(decisionProgress) else 0f,
                color = ringColor,
            ),
    ) {
        RecipeImage(
            photoUri = card.photoUri,
            title = card.title,
            modifier = Modifier.fillMaxSize(),
        )

        // Schatten von unten - weisse Schrift bleibt so auf jedem Foto lesbar.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(SCRIM_FRACTION)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to accents.photoShade.copy(alpha = 0.55f),
                        1f to accents.photoShade.copy(alpha = 0.85f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = card.title,
                // Titel aus Verzeichnissen sind oft lang - so passen sie in drei Zeilen.
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.prepTimeMinutes != null || card.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    card.prepTimeMinutes?.let { minutes ->
                        PhotoTag(text = stringResource(R.string.recipe_prep_time, minutes), withClock = true)
                    }
                    card.tags.take(MAX_VISIBLE_TAGS).forEach { tag -> PhotoTag(text = tag) }
                }
            }
        }

        // Das Info-Zeichen sagt nur: Hier gibt es mehr. Antippen laesst sich die ganze Karte.
        if (showsInfo && decisionProgress == 0f) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .clip(Tokens.Radius.CircleShape)
                    .background(accents.photoShade.copy(alpha = 0.45f))
                    .padding(8.dp)
                    .size(22.dp),
            )
        }

        if (isInteractive && decisionProgress != 0f) {
            DecisionStamp(
                progress = decisionProgress,
                modifier = Modifier
                    .align(if (decisionProgress > 0f) Alignment.TopStart else Alignment.TopEnd)
                    .padding(24.dp),
            )
        }
    }
}

/** Ein kleines Etikett auf dem Foto - weiss auf dunklem Glas. */
@Composable
private fun PhotoTag(text: String, withClock: Boolean = false) {
    Row(
        modifier = Modifier
            .clip(Tokens.Radius.PillShape)
            .background(Color.White.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), Tokens.Radius.PillShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (withClock) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

/**
 * Der Stempel "Ja ♥" / "Nein ✕", der waehrend der Geste aufgedrueckt wird: Er waechst und
 * dreht sich mit, je weiter die Karte gezogen ist.
 */
@Composable
private fun DecisionStamp(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val accents = RezeptliTheme.accents
    val liked = progress > 0f
    val farbe = if (liked) accents.swipeYes else accents.swipeNo
    val staerke = abs(progress)

    Row(
        modifier = modifier
            .graphicsLayer {
                alpha = staerke
                val scale = STAMP_MIN_SCALE + (1f - STAMP_MIN_SCALE) * staerke
                scaleX = scale
                scaleY = scale
                rotationZ = if (liked) -STAMP_TILT else STAMP_TILT
            }.clip(Tokens.Radius.MdShape)
            .background(accents.cardFill)
            .border(3.dp, farbe, Tokens.Radius.MdShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.Close,
            contentDescription = null,
            tint = farbe,
        )
        Text(
            text = stringResource(if (liked) R.string.swipe_yes else R.string.swipe_no).uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            color = farbe,
        )
    }
}

private const val SWIPE_THRESHOLD_FACTOR = 0.3f
private const val DISMISS_FACTOR = 1.6f
private const val DISMISS_DURATION_MS = 240
private const val ROTATION_DIVISOR = 36f
private const val MAX_ROTATION = 14f
private const val BACKGROUND_SCALE_STEP = 0.05f
private const val BACKGROUND_OFFSET_DP = 14f
private const val MAX_VISIBLE_TAGS = 3
private const val STAMP_MIN_SCALE = 0.6f
private const val STAMP_TILT = 12f

/** Anteil der Karte, ueber den der Schatten von unten laeuft. */
private const val SCRIM_FRACTION = 0.55f

/** Mehr gleichzeitig zu zeichnen bringt nichts - man sieht nur die obersten. */
private const val VISIBLE_CARDS = 3

package ch.rezeptli.app.presentation.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.presentation.common.components.PrepTimeLabel
import ch.rezeptli.app.presentation.common.components.RecipeImage
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
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
 * Nur die oberste Karte reagiert auf Gesten; die beiden dahinter sind leicht verkleinert
 * und angehoben, damit sichtbar ist, dass es weitergeht. Waehrend des Wischens faerbt
 * sich der Rand gruen oder rot - die Entscheidung ist damit sichtbar, bevor man loslaesst.
 */
@Composable
fun SwipeCardStack(
    cards: List<RecipeSummary>,
    onSwiped: (RecipeSummary, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    state: SwipeCardStackState = rememberSwipeCardStackState(),
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Von hinten nach vorne zeichnen, damit die oberste Karte oben liegt.
        cards.asReversed().forEachIndexed { reversedIndex, card ->
            val indexFromTop = cards.size - 1 - reversedIndex
            if (indexFromTop == 0) {
                TopSwipeCard(
                    card = card,
                    state = state,
                    onSwiped = { liked -> onSwiped(card, liked) },
                )
            } else {
                BackgroundCard(card = card, indexFromTop = indexFromTop)
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
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(card.id) { Animatable(0f) }
    var cardWidth by remember { mutableStateOf(1f) }

    val likeLabel = stringResource(R.string.swipe_yes)
    val dislikeLabel = stringResource(R.string.swipe_no)
    val cardDescription = stringResource(R.string.swipe_card_description, card.title)

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
                offsetX.animateTo(
                    targetValue = if (liked) cardWidth * DISMISS_FACTOR else -cardWidth * DISMISS_FACTOR,
                    animationSpec = tween(DISMISS_DURATION_MS),
                )
                onSwiped(liked)
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
            }.pointerInput(card.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val threshold = size.width * SWIPE_THRESHOLD_FACTOR
                            when {
                                offsetX.value > threshold -> {
                                    offsetX.animateTo(
                                        size.width * DISMISS_FACTOR,
                                        tween(DISMISS_DURATION_MS),
                                    )
                                    onSwiped(true)
                                }

                                offsetX.value < -threshold -> {
                                    offsetX.animateTo(
                                        -size.width * DISMISS_FACTOR,
                                        tween(DISMISS_DURATION_MS),
                                    )
                                    onSwiped(false)
                                }

                                else -> offsetX.animateTo(0f, tween(RETURN_DURATION_MS))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, tween(RETURN_DURATION_MS)) }
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
    )
}

@Composable
private fun BackgroundCard(
    card: RecipeSummary,
    indexFromTop: Int,
    modifier: Modifier = Modifier,
) {
    val scale = 1f - indexFromTop * BACKGROUND_SCALE_STEP
    SwipeCardContent(
        card = card,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = -indexFromTop * BACKGROUND_OFFSET_PX
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
) {
    val accents = RezeptliTheme.accents
    val borderColor = when {
        decisionProgress > 0f -> accents.swipeYes
        decisionProgress < 0f -> accents.swipeNo
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            width = if (decisionProgress == 0f) 1.dp else 3.dp,
            color = borderColor.copy(alpha = if (decisionProgress == 0f) 1f else abs(decisionProgress)),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isInteractive) 8.dp else 2.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                RecipeImage(
                    photoUri = card.photoUri,
                    title = card.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    card.prepTimeMinutes?.let { minutes -> PrepTimeLabel(minutes) }
                    if (card.tags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            card.tags.take(MAX_VISIBLE_TAGS).forEach { tag ->
                                AssistChip(onClick = {}, label = { Text(tag) })
                            }
                        }
                    }
                }
            }

            if (isInteractive && decisionProgress != 0f) {
                DecisionBadge(
                    progress = decisionProgress,
                    modifier = Modifier
                        .align(if (decisionProgress > 0f) Alignment.TopStart else Alignment.TopEnd)
                        .padding(24.dp),
                )
            }
        }
    }
}

/** "JA"/"NEIN"-Stempel, der waehrend der Geste einblendet. */
@Composable
private fun DecisionBadge(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val accents = RezeptliTheme.accents
    val liked = progress > 0f
    Card(
        modifier = modifier.alpha(abs(progress)),
        colors = CardDefaults.cardColors(
            containerColor = if (liked) accents.swipeYes else accents.swipeNo,
        ),
    ) {
        Text(
            text = stringResource(if (liked) R.string.swipe_yes else R.string.swipe_no).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

private const val SWIPE_THRESHOLD_FACTOR = 0.3f
private const val DISMISS_FACTOR = 1.6f
private const val DISMISS_DURATION_MS = 220
private const val RETURN_DURATION_MS = 200
private const val ROTATION_DIVISOR = 40f
private const val MAX_ROTATION = 12f
private const val BACKGROUND_SCALE_STEP = 0.05f
private const val BACKGROUND_OFFSET_PX = 24f
private const val MAX_VISIBLE_TAGS = 3

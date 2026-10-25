package ch.rezeptli.app.presentation.plan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.plan.PlanFailure
import ch.rezeptli.app.domain.plan.PlanStage
import ch.rezeptli.app.presentation.common.ObserveAsEvents
import ch.rezeptli.app.presentation.common.theme.Tokens

/** "Wir bauen deine Woche": die drei Schritte, die wirklich laufen. */
@Composable
fun PlanBuildRoute(
    onBuilt: () -> Unit,
    onEditPreferences: () -> Unit,
    onSkip: () -> Unit,
    viewModel: PlanBuildViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            PlanBuildEvent.Built -> onBuilt()
        }
    }

    PlanBuildScreen(
        uiState = uiState,
        onRetry = viewModel::onRetry,
        onEditPreferences = onEditPreferences,
        onSkip = onSkip,
    )
}

@Composable
fun PlanBuildScreen(
    uiState: PlanBuildUiState,
    onRetry: () -> Unit,
    onEditPreferences: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val failure = uiState.failure
        if (failure == null) {
            Building(stage = uiState.stage)
        } else {
            Failure(failure, onRetry, onEditPreferences, onSkip)
        }
    }
}

@Composable
private fun Building(stage: PlanStage) {
    val pulse by rememberInfiniteTransition(label = "Puls").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Tokens.Duration.Loader), RepeatMode.Reverse),
        label = "Puls",
    )

    Box(
        modifier = Modifier
            .size(88.dp)
            .alpha(pulse)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(40.dp))
    }
    Spacer(Modifier.size(32.dp))
    Text(
        text = stringResource(R.string.bauen_titel),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.size(8.dp))
    Text(
        text = stringResource(R.string.bauen_text),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.size(32.dp))

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StageRow(R.string.bauen_schritt_finden, PlanStage.FINDING, stage)
        StageRow(R.string.bauen_schritt_waehlen, PlanStage.CHOOSING, stage)
        StageRow(R.string.bauen_schritt_einkauf, PlanStage.SHOPPING, stage)
    }
}

@Composable
private fun StageRow(labelRes: Int, own: PlanStage, current: PlanStage) {
    val done = own.ordinal < current.ordinal
    val active = own == current
    val dot by animateColorAsState(
        targetValue = when {
            done -> MaterialTheme.colorScheme.primary
            active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(Tokens.Duration.Base),
        label = "Schrittfarbe",
    )

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(dot),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.titleMedium,
            color = if (done ||
                active
            ) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun Failure(
    failure: PlanFailure,
    onRetry: () -> Unit,
    onEditPreferences: () -> Unit,
    onSkip: () -> Unit,
) {
    val (title, text) = when (failure) {
        PlanFailure.NO_RECIPES -> R.string.bauen_fehler_keine_rezepte_titel to R.string.bauen_fehler_keine_rezepte_text
        PlanFailure.NOTHING_FITS -> R.string.bauen_fehler_passt_nichts_titel to R.string.bauen_fehler_passt_nichts_text
    }
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.size(12.dp))
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.size(32.dp))
    Button(
        onClick = onRetry,
        shape = Tokens.Radius.PillShape,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) { Text(stringResource(R.string.bauen_nochmal)) }
    if (failure == PlanFailure.NOTHING_FITS) {
        OutlinedButton(
            onClick = onEditPreferences,
            shape = Tokens.Radius.PillShape,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.bauen_vorlieben)) }
    }
    TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.bauen_ohne_plan))
    }
}

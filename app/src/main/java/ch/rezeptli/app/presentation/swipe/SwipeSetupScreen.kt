package ch.rezeptli.app.presentation.swipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.RecipeFilter

/** Kurzer Zwischenschritt vor dem Swipen: was soll auf den Stapel? */
@Composable
fun SwipeSetupRoute(
    onBack: () -> Unit,
    onStart: (RecipeFilter) -> Unit,
    viewModel: SwipeSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SwipeSetupScreen(
        uiState = uiState,
        onBack = onBack,
        onToggleTag = viewModel::onToggleTag,
        onMaxPrepTimeChange = viewModel::onMaxPrepTimeChange,
        onResetFilter = viewModel::onResetFilter,
        onStart = { onStart(uiState.filter) },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SwipeSetupScreen(
    uiState: SwipeSetupUiState,
    onBack: () -> Unit,
    onToggleTag: (String) -> Unit,
    onMaxPrepTimeChange: (Int?) -> Unit,
    onResetFilter: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.swipe_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.swipe_setup_message),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.swipe_setup_filter_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.availableTags.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.recipes_filter_tags),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.availableTags.forEach { tag ->
                        FilterChip(
                            selected = tag in uiState.selectedTags,
                            onClick = { onToggleTag(tag) },
                            label = { Text(tag) },
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.recipes_filter_time),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SETUP_PREP_TIME_OPTIONS.forEach { minutes ->
                    FilterChip(
                        selected = uiState.maxPrepTimeMinutes == minutes,
                        onClick = {
                            onMaxPrepTimeChange(
                                if (uiState.maxPrepTimeMinutes == minutes) null else minutes,
                            )
                        },
                        label = {
                            Text(
                                if (minutes == null) {
                                    stringResource(R.string.filter_time_any)
                                } else {
                                    stringResource(R.string.filter_time_minutes, minutes)
                                },
                            )
                        },
                    )
                }
            }

            Text(
                text = if (uiState.canStart) {
                    stringResource(R.string.swipe_setup_pool, uiState.matchingRecipeCount)
                } else {
                    stringResource(R.string.swipe_setup_pool_empty)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (uiState.canStart) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
            )

            Button(
                onClick = onStart,
                enabled = uiState.canStart && !uiState.isCounting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.swipe_setup_start))
            }

            TextButton(onClick = onResetFilter, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recipes_filter_reset))
            }
        }
    }
}

private val SETUP_PREP_TIME_OPTIONS: List<Int?> = listOf(null, 15, 30, 45, 60)

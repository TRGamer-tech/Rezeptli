package ch.rezeptli.app.presentation.websearch

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.WebSearchResult
import ch.rezeptli.app.presentation.common.components.EmptyState
import ch.rezeptli.app.presentation.common.theme.Tokens
import ch.rezeptli.app.presentation.common.theme.cardSurface

/** Sucht Rezepte bei den Schweizer Quellen und uebergibt einen Treffer an den Import. */
@Composable
fun WebSearchRoute(
    onBack: () -> Unit,
    onOpenResult: (String) -> Unit,
    viewModel: WebSearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WebSearchScreen(
        uiState = uiState,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onToggleSource = viewModel::onToggleSource,
        onSearch = viewModel::onSearch,
        onResultClick = { result -> onOpenResult(result.url) },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WebSearchScreen(
    uiState: WebSearchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSource: (String) -> Unit,
    onSearch: () -> Unit,
    onResultClick: (WebSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.websearch_title)) },
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
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.websearch_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Text(
                text = stringResource(R.string.websearch_sources),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                uiState.sources.forEach { source ->
                    FilterChip(
                        selected = source.id in uiState.selectedSourceIds,
                        onClick = { onToggleSource(source.id) },
                        label = { Text(source.name) },
                    )
                }
            }

            when {
                // Nichts gefunden gilt erst, wenn alle Quellen geantwortet haben.
                uiState.isEmptyResult -> EmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = stringResource(R.string.websearch_no_results_title),
                    message = stringResource(R.string.websearch_no_results_message),
                    modifier = Modifier.fillMaxSize(),
                )

                !uiState.hasSearched -> EmptyState(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.websearch_intro_title),
                    message = stringResource(R.string.websearch_intro_message),
                    modifier = Modifier.fillMaxSize(),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Treffer erscheinen, sobald ihre Quelle geantwortet hat - der Rest
                    // laedt darunter weiter.
                    items(uiState.results, key = { it.url }) { result ->
                        WebResultCard(result = result, onClick = { onResultClick(result) })
                    }

                    if (uiState.showsSkeleton) {
                        item(key = "fortschritt") {
                            SearchProgress(
                                finished = uiState.finishedSources,
                                total = uiState.totalSources,
                            )
                        }
                        items(SKELETON_COUNT, key = { "platzhalter-$it" }) {
                            SkeletonCard()
                        }
                    }
                }
            }
        }
    }
}

/** Zeigt, wie viele Quellen schon geantwortet haben - statt eines stummen Drehkreises. */
@Composable
private fun SearchProgress(finished: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = if (total > 0) {
                stringResource(R.string.websearch_quellen_fortschritt, finished, total)
            } else {
                stringResource(R.string.websearch_loading)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Ein Platzhalter in Kartenform.
 *
 * Er pulsiert leicht, damit erkennbar bleibt, dass noch etwas passiert - und er hat die
 * Groesse einer echten Karte, damit die Liste beim Eintreffen nicht springt.
 */
@Composable
private fun SkeletonCard() {
    val transition = rememberInfiniteTransition(label = "Platzhalter")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(Tokens.Duration.Loader, easing = Tokens.Ease.OutQuad),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Puls",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(Tokens.Radius.LgShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(18.dp)
                .clip(Tokens.Radius.SmShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.35f)
                .height(12.dp)
                .clip(Tokens.Radius.SmShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.6f)),
        )
    }
}

@Composable
private fun WebResultCard(
    result: WebSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = result.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = result.sourceName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** So viele Platzhalter, wie ohne Scrollen sichtbar sind. */
private const val SKELETON_COUNT = 3

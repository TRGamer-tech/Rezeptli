package ch.rezeptli.app.presentation.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.presentation.common.components.EmptyState
import ch.rezeptli.app.presentation.common.components.RecipeListCard

/** Ergebnis einer Swipe-Session: alles, was es durch die Runde geschafft hat. */
@Composable
fun ResultsRoute(
    onBackToRecipes: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onNewSession: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ResultsScreen(
        uiState = uiState,
        onBackToRecipes = onBackToRecipes,
        onRecipeClick = onRecipeClick,
        onNewSession = onNewSession,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    uiState: ResultsUiState,
    onBackToRecipes: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.results_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackToRecipes) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.results_back_to_recipes),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.matches.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onNewSession,
                    text = { Text(stringResource(R.string.results_new_session)) },
                    icon = {},
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            uiState.matches.isEmpty() -> EmptyState(
                icon = Icons.Filled.SentimentDissatisfied,
                title = stringResource(R.string.results_empty_title),
                message = stringResource(R.string.results_empty_message),
                primaryActionLabel = stringResource(R.string.results_empty_action),
                onPrimaryAction = onNewSession,
                secondaryActionLabel = stringResource(R.string.results_back_to_recipes),
                onSecondaryAction = onBackToRecipes,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    Text(
                        text = stringResource(R.string.results_count, uiState.matches.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                items(uiState.matches, key = { it.id }) { recipe ->
                    RecipeListCard(
                        recipe = recipe,
                        onClick = { onRecipeClick(recipe.id) },
                    )
                }
            }
        }
    }
}

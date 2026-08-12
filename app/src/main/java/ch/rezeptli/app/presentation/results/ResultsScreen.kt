package ch.rezeptli.app.presentation.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.presentation.common.ObserveAsEvents
import ch.rezeptli.app.presentation.common.components.EmptyState
import ch.rezeptli.app.presentation.common.components.RecipeListCard
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import kotlinx.coroutines.launch

/** Ergebnis einer Swipe-Session: alles, was es durch die Runde geschafft hat. */
@Composable
fun ResultsRoute(
    onBackToRecipes: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onNewSession: () -> Unit,
    onOpenShoppingList: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ResultsEvent.AddedToShoppingList -> scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.results_added_to_shopping, event.itemCount),
                    actionLabel = context.getString(R.string.shopping_title),
                )
                if (result == SnackbarResult.ActionPerformed) onOpenShoppingList()
            }
        }
    }

    ResultsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackToRecipes = onBackToRecipes,
        onRecipeClick = onRecipeClick,
        onNewSession = onNewSession,
        onOpenShoppingList = onOpenShoppingList,
        onAddToShoppingList = viewModel::onAddToShoppingList,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    uiState: ResultsUiState,
    snackbarHostState: SnackbarHostState,
    onBackToRecipes: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onNewSession: () -> Unit,
    onOpenShoppingList: () -> Unit,
    onAddToShoppingList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            RezeptliTopBar(
                title = stringResource(R.string.results_title),
                onBack = onBackToRecipes,
                actions = {
                    IconButton(onClick = onOpenShoppingList) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = stringResource(R.string.shopping_title),
                        )
                    }
                    IconButton(onClick = onNewSession) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.results_new_session),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.matches.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddToShoppingList,
                    text = { Text(stringResource(R.string.results_add_to_shopping)) },
                    icon = {
                        Icon(imageVector = Icons.Filled.AddShoppingCart, contentDescription = null)
                    },
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

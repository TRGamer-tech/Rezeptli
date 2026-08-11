package ch.rezeptli.app.presentation.recipelist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.presentation.common.components.EmptyState
import ch.rezeptli.app.presentation.common.components.RecipeListCard
import kotlinx.coroutines.launch

/** Einstiegspunkt der App: alle Rezepte, Suche, Filter und der Weg in den Swipe-Modus. */
@Composable
fun RecipeListRoute(
    onRecipeClick: (Long) -> Unit,
    onCreateRecipe: () -> Unit,
    onImportRecipe: () -> Unit,
    onStartSwipe: () -> Unit,
    onOpenShoppingList: () -> Unit,
    viewModel: RecipeListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recipes = viewModel.recipes.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val swipeNeedsRecipes = stringResource(R.string.recipes_swipe_needs_recipes)

    RecipeListScreen(
        uiState = uiState,
        recipes = recipes,
        snackbarHostState = snackbarHostState,
        onQueryChange = viewModel::onQueryChange,
        onToggleTag = viewModel::onToggleTag,
        onMaxPrepTimeChange = viewModel::onMaxPrepTimeChange,
        onFilterVisibilityChange = viewModel::onFilterVisibilityChange,
        onResetFilter = viewModel::onResetFilter,
        onRecipeClick = onRecipeClick,
        onCreateRecipe = onCreateRecipe,
        onImportRecipe = onImportRecipe,
        onSearchWeb = onSearchWeb,
        onOpenShoppingList = onOpenShoppingList,
        onStartSwipe = {
            if (uiState.hasAnyRecipes) {
                onStartSwipe()
            } else {
                scope.launch { snackbarHostState.showSnackbar(swipeNeedsRecipes) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    uiState: RecipeListUiState,
    recipes: LazyPagingItems<RecipeSummary>,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onMaxPrepTimeChange: (Int?) -> Unit,
    onFilterVisibilityChange: (Boolean) -> Unit,
    onResetFilter: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onCreateRecipe: () -> Unit,
    onImportRecipe: () -> Unit,
    onSearchWeb: () -> Unit,
    onOpenShoppingList: () -> Unit,
    onStartSwipe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
                actions = {
                    if (uiState.openShoppingItems > 0) {
                        BadgedBox(
                            badge = { Badge { Text(uiState.openShoppingItems.toString()) } },
                        ) {
                            IconButton(onClick = onOpenShoppingList) {
                                Icon(
                                    imageVector = Icons.Filled.ShoppingCart,
                                    contentDescription = stringResource(
                                        R.string.shopping_open_badge,
                                        uiState.openShoppingItems,
                                    ),
                                )
                            }
                        }
                    }
                    IconButton(onClick = onSearchWeb) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = stringResource(R.string.websearch_open),
                        )
                    }
                    IconButton(onClick = onImportRecipe) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = stringResource(R.string.recipes_import),
                        )
                    }
                    IconButton(onClick = { onFilterVisibilityChange(!uiState.isFilterVisible) }) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = stringResource(R.string.recipes_filter),
                            tint = if (uiState.isFilterActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FloatingActionButton(
                    onClick = onCreateRecipe,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.recipes_add),
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = onStartSwipe,
                    icon = {
                        Icon(imageVector = Icons.Filled.SwipeRight, contentDescription = null)
                    },
                    text = { Text(stringResource(R.string.recipes_start_swipe)) },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchField(
                query = uiState.query,
                onQueryChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            AnimatedVisibility(visible = uiState.isFilterVisible) {
                FilterSection(
                    uiState = uiState,
                    onToggleTag = onToggleTag,
                    onMaxPrepTimeChange = onMaxPrepTimeChange,
                    onResetFilter = onResetFilter,
                )
            }

            RecipeListContent(
                uiState = uiState,
                recipes = recipes,
                onRecipeClick = onRecipeClick,
                onCreateRecipe = onCreateRecipe,
                onImportRecipe = onImportRecipe,
                onSearchWeb = onSearchWeb,
                onResetFilter = onResetFilter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RecipeListContent(
    uiState: RecipeListUiState,
    recipes: LazyPagingItems<RecipeSummary>,
    onRecipeClick: (Long) -> Unit,
    onCreateRecipe: () -> Unit,
    onImportRecipe: () -> Unit,
    onSearchWeb: () -> Unit,
    onResetFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = recipes.loadState.refresh is LoadState.Loading
    val isEmpty = recipes.itemCount == 0 && !isLoading

    when {
        isEmpty && !uiState.hasAnyRecipes -> EmptyState(
            icon = Icons.Filled.MenuBook,
            title = stringResource(R.string.recipes_empty_title),
            message = stringResource(R.string.recipes_empty_message),
            primaryActionLabel = stringResource(R.string.recipes_empty_action_create),
            onPrimaryAction = onCreateRecipe,
            secondaryActionLabel = stringResource(R.string.websearch_open),
            onSecondaryAction = onSearchWeb,
            modifier = modifier,
        )

        isEmpty -> EmptyState(
            icon = Icons.Filled.SearchOff,
            title = stringResource(R.string.recipes_no_results_title),
            message = stringResource(R.string.recipes_no_results_message),
            primaryActionLabel = stringResource(R.string.recipes_filter_reset),
            onPrimaryAction = onResetFilter,
            modifier = modifier,
        )

        isLoading -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                count = recipes.itemCount,
                key = recipes.itemKey { it.id },
            ) { index ->
                val recipe = recipes[index]
                if (recipe != null) {
                    RecipeListCard(
                        recipe = recipe,
                        onClick = { onRecipeClick(recipe.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.recipes_search_hint)) },
        leadingIcon = {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.recipes_search_clear),
                    )
                }
            }
        },
        singleLine = true,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    uiState: RecipeListUiState,
    onToggleTag: (String) -> Unit,
    onMaxPrepTimeChange: (Int?) -> Unit,
    onResetFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
            PREP_TIME_OPTIONS.forEach { minutes ->
                FilterChip(
                    selected = uiState.maxPrepTimeMinutes == minutes,
                    onClick = {
                        onMaxPrepTimeChange(if (uiState.maxPrepTimeMinutes == minutes) null else minutes)
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

        if (uiState.isFilterActive) {
            TextButton(onClick = onResetFilter) {
                Text(stringResource(R.string.recipes_filter_reset))
            }
        }
    }
}

private val PREP_TIME_OPTIONS: List<Int?> = listOf(null, 15, 30, 45, 60)

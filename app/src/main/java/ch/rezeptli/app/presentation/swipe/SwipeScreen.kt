package ch.rezeptli.app.presentation.swipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.presentation.common.ObserveAsEvents
import ch.rezeptli.app.presentation.common.components.EmptyState
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import kotlinx.coroutines.launch

/** Der Swipe-Modus: ein Rezept pro Karte, wischen oder tippen. */
@Composable
fun SwipeRoute(
    onBack: () -> Unit,
    onShowResults: (Long) -> Unit,
    viewModel: SwipeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoMessage = stringResource(R.string.swipe_undo_done)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SwipeEvent.ShowResults -> onShowResults(event.sessionId)
            SwipeEvent.Exit -> onBack()
            SwipeEvent.UndoDone -> scope.launch { snackbarHostState.showSnackbar(undoMessage) }
        }
    }

    SwipeScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onSwiped = { recipe, liked -> viewModel.onSwipe(recipe.id, liked) },
        onUndo = viewModel::onUndo,
        onShowResults = viewModel::onShowResults,
        onExitRequest = viewModel::onExitRequest,
        onExitDismiss = viewModel::onExitDismiss,
        onExitConfirm = viewModel::onExitConfirm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(
    uiState: SwipeUiState,
    snackbarHostState: SnackbarHostState,
    onSwiped: (RecipeSummary, Boolean) -> Unit,
    onUndo: () -> Unit,
    onShowResults: () -> Unit,
    onExitRequest: () -> Unit,
    onExitDismiss: () -> Unit,
    onExitConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardStackState = rememberSwipeCardStackState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.swipe_title)) },
                navigationIcon = {
                    IconButton(onClick = onExitRequest) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.swipe_finish),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onUndo, enabled = uiState.canUndo) {
                        Icon(
                            imageVector = Icons.Filled.Undo,
                            contentDescription = stringResource(R.string.action_undo),
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
            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                uiState.isFinished -> EmptyState(
                    icon = Icons.Filled.DoneAll,
                    title = stringResource(R.string.swipe_nothing_left),
                    message = stringResource(
                        R.string.swipe_progress,
                        uiState.decidedCount,
                        uiState.totalCount,
                    ),
                    primaryActionLabel = stringResource(R.string.swipe_show_results),
                    onPrimaryAction = onShowResults,
                    modifier = Modifier.fillMaxSize(),
                )

                else -> {
                    SwipeProgress(
                        decided = uiState.decidedCount,
                        total = uiState.totalCount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    SwipeCardStack(
                        cards = uiState.cards,
                        onSwiped = onSwiped,
                        state = cardStackState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    )

                    DecisionButtons(
                        onNo = { cardStackState.swipe(false) },
                        onYes = { cardStackState.swipe(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 20.dp),
                    )
                }
            }
        }
    }

    if (uiState.isExitDialogVisible) {
        AlertDialog(
            onDismissRequest = onExitDismiss,
            title = { Text(stringResource(R.string.swipe_exit_title)) },
            text = { Text(stringResource(R.string.swipe_exit_message)) },
            confirmButton = {
                TextButton(onClick = onExitConfirm) {
                    Text(stringResource(R.string.swipe_exit_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onExitDismiss) {
                    Text(stringResource(R.string.swipe_exit_keep))
                }
            },
        )
    }
}

@Composable
private fun SwipeProgress(
    decided: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.swipe_progress, decided, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else decided.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Ja und Nein auch als Schaltflaechen - nicht jede und jeder kann oder will wischen,
 * und fuer Screenreader ist das der zuverlaessige Weg.
 */
@Composable
private fun DecisionButtons(
    onNo: () -> Unit,
    onYes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = RezeptliTheme.accents

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onNo,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = accents.swipeNo.copy(alpha = 0.15f),
                contentColor = accents.swipeNo,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.swipe_no),
                modifier = Modifier.size(32.dp),
            )
        }

        FilledTonalIconButton(
            onClick = onYes,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = accents.swipeYes.copy(alpha = 0.15f),
                contentColor = accents.swipeYes,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.swipe_yes),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

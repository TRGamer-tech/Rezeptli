package ch.rezeptli.app.presentation.shoppinglist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.AmountFormatter
import ch.rezeptli.app.domain.model.IngredientCategory
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.presentation.common.components.EmptyState
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.formatChf
import ch.rezeptli.app.presentation.common.label
import ch.rezeptli.app.presentation.common.shareText
import kotlinx.coroutines.launch

/** Die Einkaufsliste: nach Warengruppen sortiert, zum Abhaken. */
@Composable
fun ShoppingListRoute(
    onBack: () -> Unit,
    viewModel: ShoppingListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listTitle = stringResource(R.string.shopping_title)
    val copiedMessage = stringResource(R.string.einkauf_kopiert)

    ShoppingListScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onCopy = { text ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(listTitle, text))
            scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
        },
        onShare = { text -> context.shareText(text) },
        onCheckedChange = viewModel::onCheckedChange,
        onDelete = viewModel::onDelete,
        onNewItemNameChange = viewModel::onNewItemNameChange,
        onAddItem = viewModel::onAddItem,
        onRemoveChecked = viewModel::onRemoveChecked,
        onClearRequest = viewModel::onClearRequest,
        onClearDismiss = viewModel::onClearDismiss,
        onClearConfirm = viewModel::onClearConfirm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    uiState: ShoppingListUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onCheckedChange: (ShoppingItem, Boolean) -> Unit,
    onDelete: (ShoppingItem) -> Unit,
    onNewItemNameChange: (String) -> Unit,
    onAddItem: () -> Unit,
    onRemoveChecked: () -> Unit,
    onClearRequest: () -> Unit,
    onClearDismiss: () -> Unit,
    onClearConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            RezeptliTopBar(
                title = stringResource(R.string.shopping_title),
                onBack = onBack,
                actions = {
                    if (!uiState.isEmpty) {
                        val context = LocalContext.current
                        IconButton(onClick = { onCopy(uiState.asText(context)) }) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.einkauf_kopieren),
                            )
                        }
                        IconButton(onClick = { onShare(uiState.asText(context)) }) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = stringResource(R.string.einkauf_teilen),
                            )
                        }
                    }
                    if (uiState.checkedCount > 0) {
                        IconButton(onClick = onRemoveChecked) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = stringResource(R.string.shopping_remove_checked),
                            )
                        }
                    }
                    if (!uiState.isEmpty) {
                        IconButton(onClick = onClearRequest) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.shopping_clear),
                            )
                        }
                    }
                },
            )
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

            uiState.isEmpty -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                EmptyState(
                    icon = Icons.Filled.ShoppingCart,
                    title = stringResource(R.string.shopping_empty_title),
                    message = stringResource(R.string.shopping_empty_message),
                    modifier = Modifier.weight(1f),
                )
                NewItemField(
                    value = uiState.newItemName,
                    onValueChange = onNewItemNameChange,
                    onAdd = onAddItem,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    item(key = "summary") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.shopping_summary,
                                    uiState.openCount,
                                    uiState.items.size,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (uiState.estimatedTotal > 0.0) {
                                Text(
                                    text = stringResource(
                                        R.string.einkauf_gesamt,
                                        formatChf(uiState.estimatedTotal),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    uiState.grouped.forEach { (category, items) ->
                        item(key = "header-${category.name}") {
                            CategoryHeader(category)
                        }
                        items(items, key = { it.id }) { item ->
                            ShoppingItemRow(
                                item = item,
                                onCheckedChange = { checked -> onCheckedChange(item, checked) },
                                onDelete = { onDelete(item) },
                            )
                        }
                    }
                }

                HorizontalDivider()
                NewItemField(
                    value = uiState.newItemName,
                    onValueChange = onNewItemNameChange,
                    onAdd = onAddItem,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }

    if (uiState.isClearDialogVisible) {
        AlertDialog(
            onDismissRequest = onClearDismiss,
            title = { Text(stringResource(R.string.shopping_clear_title)) },
            text = { Text(stringResource(R.string.shopping_clear_message)) },
            confirmButton = {
                TextButton(onClick = onClearConfirm) {
                    Text(stringResource(R.string.shopping_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = onClearDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CategoryHeader(category: IngredientCategory, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(category.labelRes()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItem,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!item.isChecked) }
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = onCheckedChange,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (item.packText != null) item.name else item.displayText(),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                color = if (item.isChecked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (item.packText != null) {
                val needed = item.displayText().removeSuffix(" " + item.name).takeIf { it != item.name }
                val detail = listOfNotNull(
                    needed?.let { stringResource(R.string.einkauf_benoetigt, it) },
                    item.sourceNote,
                ).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item.sourceNote?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item.packText?.let { pack ->
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = pack,
                    style = MaterialTheme.typography.labelLarge,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                )
                item.price?.let { price ->
                    Text(
                        text = "CHF " + formatChf(price),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.shopping_delete_item, item.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NewItemField(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(R.string.shopping_add_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onAdd,
            enabled = value.isNotBlank(),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.shopping_add),
            )
        }
    }
}

/** "500 g Mehl", "2 Bund Peterli", "Salz". */
@Composable
private fun ShoppingItem.displayText(): String {
    val amountText = AmountFormatter.format(amount)
    val unitText = if (unit == IngredientUnit.NONE) "" else unit.label()
    val quantity = listOf(amountText, unitText).filter { it.isNotBlank() }.joinToString(" ")
    return listOf(quantity, name).filter { it.isNotBlank() }.joinToString(" ")
}

private fun IngredientCategory.labelRes(): Int = when (this) {
    IngredientCategory.GEMUESE -> R.string.category_gemuese
    IngredientCategory.FRUECHTE -> R.string.category_fruechte
    IngredientCategory.MILCHPRODUKTE -> R.string.category_milchprodukte
    IngredientCategory.FLEISCH_FISCH -> R.string.category_fleisch_fisch
    IngredientCategory.TEIGWAREN_GETREIDE -> R.string.category_teigwaren
    IngredientCategory.KONSERVEN_SAUCEN -> R.string.category_konserven
    IngredientCategory.BACKEN_SUESSES -> R.string.category_backen
    IngredientCategory.GEWUERZE_KRAEUTER -> R.string.category_gewuerze
    IngredientCategory.TIEFKUEHL -> R.string.category_tiefkuehl
    IngredientCategory.GETRAENKE -> R.string.category_getraenke
    IngredientCategory.SONSTIGES -> R.string.category_sonstiges
}

/** Die Liste als Text zum Kopieren und Teilen: je Warengruppe eine Ueberschrift, je Posten eine Zeile. */
private fun ShoppingListUiState.asText(context: Context): String = buildString {
    grouped.forEach { (category, items) ->
        appendLine(context.getString(category.labelRes()))
        items.forEach { item ->
            val quantity = listOfNotNull(
                AmountFormatter.format(item.amount).takeIf { it.isNotBlank() },
                item.unit.abbreviation.takeIf { it.isNotBlank() },
            ).joinToString(" ")
            val line = listOf(quantity, item.name).filter { it.isNotBlank() }.joinToString(" ")
            val pack = item.packText?.let { " ($it)" }.orEmpty()
            appendLine("${if (item.isChecked) "[x]" else "[ ]"} $line$pack")
        }
        appendLine()
    }
}.trim()

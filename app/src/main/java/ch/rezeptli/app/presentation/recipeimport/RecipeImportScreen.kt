package ch.rezeptli.app.presentation.recipeimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.repository.WebImportError
import ch.rezeptli.app.presentation.common.ObserveAsEvents
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.form.RecipeFormActions
import ch.rezeptli.app.presentation.common.form.RecipeFormState
import ch.rezeptli.app.presentation.common.form.recipeFormFields

/**
 * Import eines kopierten Rezepttexts in zwei Schritten: einfuegen, dann den erkannten
 * Vorschlag pruefen und korrigieren.
 */
@Composable
fun RecipeImportRoute(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: RecipeImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RecipeImportEvent.Saved -> onSaved(event.recipeId)
        }
    }

    RecipeImportScreen(
        uiState = uiState,
        actions = RecipeFormActions(
            onTitleChange = viewModel::onTitleChange,
            onInstructionsChange = viewModel::onInstructionsChange,
            onPrepTimeChange = viewModel::onPrepTimeChange,
            onTagInputChange = viewModel::onTagInputChange,
            onTagAdd = viewModel::onTagAdd,
            onTagRemove = viewModel::onTagRemove,
            onIngredientChange = viewModel::onIngredientChange,
            onIngredientRemove = viewModel::onIngredientRemove,
            onIngredientAdd = viewModel::onIngredientAdd,
        ),
        onTextChange = viewModel::onTextChange,
        onClearText = viewModel::onClearText,
        onAnalyse = viewModel::onAnalyse,
        onBack = { if (uiState.isPreviewVisible) viewModel.onBackToText() else onBack() },
        onSave = viewModel::onSave,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeImportScreen(
    uiState: RecipeImportUiState,
    actions: RecipeFormActions,
    onTextChange: (String) -> Unit,
    onClearText: () -> Unit,
    onAnalyse: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            RezeptliTopBar(
                title = stringResource(R.string.import_title),
                onBack = onBack,
                actions = {
                    if (uiState.isPreviewVisible) {
                        TextButton(onClick = onSave, enabled = !uiState.isSaving) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val form = uiState.form
        if (form == null) {
            TextInputStep(
                uiState = uiState,
                onTextChange = onTextChange,
                onClearText = onClearText,
                onAnalyse = onAnalyse,
                contentPadding = innerPadding,
            )
        } else {
            PreviewStep(
                form = form,
                actions = actions,
                hasUncertainLines = uiState.hasUncertainLines,
                ingredientCount = uiState.recognisedIngredientCount,
                isSaving = uiState.isSaving,
                onSave = onSave,
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun TextInputStep(
    uiState: RecipeImportUiState,
    onTextChange: (String) -> Unit,
    onClearText: () -> Unit,
    onAnalyse: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val rawText = uiState.rawText
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.import_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = rawText,
            onValueChange = onTextChange,
            label = { Text(stringResource(R.string.import_url_hint)) },
            minLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )

        if (uiState.nothingFound) {
            InfoCard(
                text = stringResource(R.string.import_nothing_found),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        uiState.webError?.let { error ->
            InfoCard(
                text = stringResource(error.messageRes()),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        if (uiState.isLoadingFromWeb) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.import_loading_web),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAnalyse,
                enabled = rawText.isNotBlank() && !uiState.isLoadingFromWeb,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(
                        if (uiState.looksLikeUrl) R.string.import_analyse_url else R.string.import_analyse,
                    ),
                )
            }
            if (rawText.isNotEmpty()) {
                TextButton(onClick = onClearText) {
                    Text(stringResource(R.string.import_clear))
                }
            }
        }

        if (rawText.isBlank()) {
            val example = stringResource(R.string.import_example_text)
            TextButton(onClick = { onTextChange(example) }) {
                Text(stringResource(R.string.import_example))
            }
        }
    }
}

@Composable
private fun PreviewStep(
    form: RecipeFormState,
    actions: RecipeFormActions,
    hasUncertainLines: Boolean,
    ingredientCount: Int,
    isSaving: Boolean,
    onSave: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "resultHeader") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.import_result_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = if (ingredientCount > 0) {
                        stringResource(R.string.import_ingredients_found, ingredientCount)
                    } else {
                        stringResource(R.string.import_no_ingredients_found)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (hasUncertainLines) {
            item(key = "uncertainHint") {
                InfoCard(
                    text = stringResource(R.string.import_result_hint),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        recipeFormFields(state = form, actions = actions)

        item(key = "save") {
            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun InfoCard(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Fehlermeldungen, die erklaeren was zu tun ist statt nur was schiefging. */
private fun WebImportError.messageRes(): Int = when (this) {
    WebImportError.NO_CONNECTION -> R.string.error_no_connection
    WebImportError.REJECTED -> R.string.error_rejected
    WebImportError.DISALLOWED -> R.string.error_disallowed
    WebImportError.NOT_FOUND -> R.string.error_not_found
    WebImportError.NO_RECIPE_FOUND -> R.string.error_no_recipe
    WebImportError.UNKNOWN -> R.string.error_unknown
}

package ch.rezeptli.app.presentation.recipeedit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ch.rezeptli.app.presentation.common.ObserveAsEvents
import ch.rezeptli.app.presentation.common.components.RecipeImage
import ch.rezeptli.app.presentation.common.form.RecipeFormActions
import ch.rezeptli.app.presentation.common.form.RecipeFormState
import ch.rezeptli.app.presentation.common.form.recipeFormFields

/** Rezept anlegen oder bearbeiten. */
@Composable
fun RecipeEditRoute(
    onDone: (Long) -> Unit,
    onCancel: () -> Unit,
    viewModel: RecipeEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RecipeEditEvent.Saved -> onDone(event.recipeId)
            RecipeEditEvent.Discarded -> onCancel()
        }
    }

    RecipeEditScreen(
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
        onPhotoSelected = viewModel::onPhotoSelected,
        onPhotoRemove = viewModel::onPhotoRemove,
        onSave = viewModel::onSave,
        onBack = viewModel::onBackRequest,
        onDiscardDismiss = viewModel::onDiscardDismiss,
        onDiscardConfirm = viewModel::onDiscardConfirm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    uiState: RecipeEditUiState,
    actions: RecipeFormActions,
    onPhotoSelected: (android.net.Uri?) -> Unit,
    onPhotoRemove: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onDiscardDismiss: () -> Unit,
    onDiscardConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = onPhotoSelected,
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isNewRecipe) R.string.edit_title_new else R.string.edit_title_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !uiState.isSaving) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            RecipeEditForm(
                form = uiState.form,
                actions = actions,
                contentPadding = innerPadding,
                onPickPhoto = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onPhotoRemove = onPhotoRemove,
                onSave = onSave,
                isSaving = uiState.isSaving,
            )
        }
    }

    if (uiState.isDiscardDialogVisible) {
        AlertDialog(
            onDismissRequest = onDiscardDismiss,
            title = { Text(stringResource(R.string.edit_discard_title)) },
            text = { Text(stringResource(R.string.edit_discard_message)) },
            confirmButton = {
                TextButton(onClick = onDiscardConfirm) {
                    Text(stringResource(R.string.edit_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDiscardDismiss) {
                    Text(stringResource(R.string.edit_discard_keep))
                }
            },
        )
    }
}

@Composable
private fun RecipeEditForm(
    form: RecipeFormState,
    actions: RecipeFormActions,
    contentPadding: PaddingValues,
    onPickPhoto: () -> Unit,
    onPhotoRemove: () -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
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
        item(key = "photo") {
            RecipeImage(
                photoUri = form.photoUri,
                title = form.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }

        item(key = "photoActions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickPhoto) {
                    Icon(imageVector = Icons.Filled.AddAPhoto, contentDescription = null)
                    Text(
                        text = stringResource(
                            if (form.photoUri == null) R.string.edit_photo_add else R.string.edit_photo_change,
                        ),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (form.photoUri != null) {
                    OutlinedButton(onClick = onPhotoRemove) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.edit_photo_remove),
                        )
                    }
                }
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

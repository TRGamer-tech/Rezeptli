package ch.rezeptli.app.presentation.common.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.presentation.common.label

/** Aktionen, die das Rezept-Formular ausloest. Der Zustand selbst lebt im ViewModel. */
data class RecipeFormActions(
    val onTitleChange: (String) -> Unit,
    val onInstructionsChange: (String) -> Unit,
    val onPrepTimeChange: (String) -> Unit,
    val onTagInputChange: (String) -> Unit,
    val onTagAdd: () -> Unit,
    val onTagRemove: (String) -> Unit,
    val onIngredientChange: (Int, IngredientDraft) -> Unit,
    val onIngredientRemove: (Int) -> Unit,
    val onIngredientAdd: () -> Unit,
)

/**
 * Die Formularfelder eines Rezepts als Bausteine einer LazyColumn.
 *
 * Bearbeiten und Import teilen sich dieselben Felder: Wer ein importiertes Rezept
 * korrigiert, arbeitet mit genau derselben Oberflaeche wie beim normalen Bearbeiten.
 */
fun LazyListScope.recipeFormFields(
    state: RecipeFormState,
    actions: RecipeFormActions,
) {
    item(key = "title") {
        OutlinedTextField(
            value = state.title,
            onValueChange = actions.onTitleChange,
            label = { Text(stringResource(R.string.edit_field_title)) },
            placeholder = { Text(stringResource(R.string.edit_field_title_hint)) },
            isError = state.titleError,
            supportingText = if (state.titleError) {
                { Text(stringResource(R.string.edit_error_title_blank)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    item(key = "prepTime") {
        OutlinedTextField(
            value = state.prepTimeText,
            onValueChange = actions.onPrepTimeChange,
            label = { Text(stringResource(R.string.edit_field_prep_time)) },
            isError = state.prepTimeError,
            supportingText = if (state.prepTimeError) {
                { Text(stringResource(R.string.edit_error_prep_time)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    item(key = "tags") {
        TagEditor(
            tags = state.tags,
            tagInput = state.tagInput,
            onTagInputChange = actions.onTagInputChange,
            onTagAdd = actions.onTagAdd,
            onTagRemove = actions.onTagRemove,
        )
    }

    item(key = "ingredientsHeader") {
        Text(
            text = stringResource(R.string.edit_ingredients),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    itemsIndexed(
        items = state.ingredients,
        key = { _, draft -> "ingredient-${draft.key}" },
    ) { index, draft ->
        IngredientRowEditor(
            draft = draft,
            onChange = { actions.onIngredientChange(index, it) },
            onRemove = { actions.onIngredientRemove(index) },
        )
    }

    item(key = "addIngredient") {
        OutlinedButton(
            onClick = actions.onIngredientAdd,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.edit_add_ingredient),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    item(key = "instructions") {
        OutlinedTextField(
            value = state.instructions,
            onValueChange = actions.onInstructionsChange,
            label = { Text(stringResource(R.string.edit_field_instructions)) },
            placeholder = { Text(stringResource(R.string.edit_field_instructions_hint)) },
            minLines = 5,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

/** Tag-Eingabe mit Chips fuer die bereits vergebenen Tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditor(
    tags: List<String>,
    tagInput: String,
    onTagInputChange: (String) -> Unit,
    onTagAdd: () -> Unit,
    onTagRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = tagInput,
                onValueChange = onTagInputChange,
                label = { Text(stringResource(R.string.edit_field_tags_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onTagAdd,
                enabled = tagInput.isNotBlank(),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.edit_add_tag),
                )
            }
        }
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onTagRemove(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.edit_remove_tag, tag),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Eine Zutatenzeile: Menge, Einheit, Name und optionale Notiz. */
@Composable
private fun IngredientRowEditor(
    draft: IngredientDraft,
    onChange: (IngredientDraft) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (draft.needsReview) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (draft.needsReview) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.import_uncertain_description),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.import_uncertain),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                draft.rawLine?.let { raw ->
                    Text(
                        text = stringResource(R.string.import_original_line, raw),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft.amountText,
                    onValueChange = { onChange(draft.copy(amountText = it)) },
                    label = { Text(stringResource(R.string.edit_ingredient_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(104.dp),
                )
                UnitDropdown(
                    unit = draft.unit,
                    onUnitChange = { onChange(draft.copy(unit = it)) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.edit_remove_ingredient),
                    )
                }
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it, canonicalName = null)) },
                label = { Text(stringResource(R.string.edit_ingredient_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.note,
                onValueChange = { onChange(draft.copy(note = it)) },
                label = { Text(stringResource(R.string.edit_ingredient_note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Auswahl der Einheit. Bewusst eine kompakte Liste statt eines Freitextfelds. */
@Composable
private fun UnitDropdown(
    unit: IngredientUnit,
    onUnitChange: (IngredientUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(unit.label()) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(R.string.edit_ingredient_unit),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            IngredientUnit.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.label()) },
                    onClick = {
                        onUnitChange(candidate)
                        expanded = false
                    },
                )
            }
        }
    }
}

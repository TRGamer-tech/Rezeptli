package ch.rezeptli.app.presentation.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.model.IngredientCategory
import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.usecase.AddManualShoppingItemUseCase
import ch.rezeptli.app.domain.usecase.ClearShoppingListUseCase
import ch.rezeptli.app.domain.usecase.DeleteShoppingItemUseCase
import ch.rezeptli.app.domain.usecase.ObserveShoppingListUseCase
import ch.rezeptli.app.domain.usecase.SetShoppingItemCheckedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShoppingListUiState(
    val isLoading: Boolean = true,
    val items: List<ShoppingItem> = emptyList(),
    val newItemName: String = "",
    val isClearDialogVisible: Boolean = false,
) {
    /** Nach Warengruppe gebuendelt, in der Reihenfolge des Ladenrundgangs. */
    val grouped: List<Pair<IngredientCategory, List<ShoppingItem>>>
        get() = items
            .groupBy { it.category }
            .toList()
            .sortedBy { (category, _) -> category.ordinal }

    val openCount: Int get() = items.count { !it.isChecked }

    val checkedCount: Int get() = items.count { it.isChecked }

    val isEmpty: Boolean get() = items.isEmpty()
}

sealed interface ShoppingListEvent {
    data class ItemDeleted(val item: ShoppingItem) : ShoppingListEvent
}

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    observeShoppingList: ObserveShoppingListUseCase,
    private val setChecked: SetShoppingItemCheckedUseCase,
    private val deleteItem: DeleteShoppingItemUseCase,
    private val addManualItem: AddManualShoppingItemUseCase,
    private val clearShoppingList: ClearShoppingListUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()

    private val _events = Channel<ShoppingListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        observeShoppingList()
            .onEach { items -> _uiState.update { it.copy(items = items, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    fun onCheckedChange(item: ShoppingItem, checked: Boolean) {
        viewModelScope.launch { setChecked(item.id, checked) }
    }

    fun onDelete(item: ShoppingItem) {
        viewModelScope.launch {
            deleteItem(item.id)
            _events.send(ShoppingListEvent.ItemDeleted(item))
        }
    }

    fun onNewItemNameChange(name: String) {
        _uiState.update { it.copy(newItemName = name) }
    }

    fun onAddItem() {
        val name = _uiState.value.newItemName
        if (name.isBlank()) return
        viewModelScope.launch {
            addManualItem(name)
            _uiState.update { it.copy(newItemName = "") }
        }
    }

    fun onRemoveChecked() {
        viewModelScope.launch { clearShoppingList.checkedOnly() }
    }

    fun onClearRequest() {
        _uiState.update { it.copy(isClearDialogVisible = true) }
    }

    fun onClearDismiss() {
        _uiState.update { it.copy(isClearDialogVisible = false) }
    }

    fun onClearConfirm() {
        _uiState.update { it.copy(isClearDialogVisible = false) }
        viewModelScope.launch { clearShoppingList.everything() }
    }
}

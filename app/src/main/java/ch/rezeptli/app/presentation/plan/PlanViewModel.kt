package ch.rezeptli.app.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.plan.ObservePlanUseCase
import ch.rezeptli.app.domain.plan.PlanEntry
import ch.rezeptli.app.domain.plan.ReplaceMealUseCase
import ch.rezeptli.app.domain.profile.StoreChain
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.repository.UserProfileRepository
import ch.rezeptli.app.domain.usecase.ObserveShoppingListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val store: StoreChain? = null,
    val budget: Int = UserProfile.DEFAULT_BUDGET,
    val expectedMeals: Int = UserProfile.DEFAULT_COOK_DAYS,
    val meals: List<PlanEntry> = emptyList(),
    val itemsTotal: Int = 0,
    val itemsChecked: Int = 0,
    /** Was der Einkauf des Plans kostet, mit ganzen Packungen. */
    val estimatedCost: Double = 0.0,
    val replacingDay: Weekday? = null,
    val showRebuildDialog: Boolean = false,
) {
    val hasPlan: Boolean get() = meals.isNotEmpty()

    val isOverBudget: Boolean get() = estimatedCost > budget

    /** Wie weit das Budget aufgebraucht ist, fuer den Balken: zwischen 0 und 1. */
    val budgetShare: Float get() = if (budget <= 0) 0f else (estimatedCost / budget).toFloat().coerceIn(0f, 1f)
}

sealed interface PlanEvent {
    data object NoAlternative : PlanEvent
}

/** Die Startseite: Woche, Kosten und Einkauf auf einen Blick. */
@HiltViewModel
class PlanViewModel @Inject constructor(
    profileRepository: UserProfileRepository,
    observePlan: ObservePlanUseCase,
    observeShoppingList: ObserveShoppingListUseCase,
    private val replaceMeal: ReplaceMealUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<PlanUiState> = combine(
        profileRepository.profile,
        observePlan(),
        observeShoppingList(),
        local,
    ) { profile, meals, items, state ->
        val planItems = items.filter { it.fromPlan }
        PlanUiState(
            isLoading = false,
            name = profile.firstName,
            store = profile.store,
            budget = profile.weeklyBudget ?: UserProfile.DEFAULT_BUDGET,
            expectedMeals = profile.mealsPerWeek,
            meals = meals,
            itemsTotal = items.size,
            itemsChecked = items.count { it.isChecked },
            estimatedCost = planItems.sumOf { it.price ?: 0.0 },
            replacingDay = state.replacingDay,
            showRebuildDialog = state.showRebuildDialog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlanUiState(),
    )

    private val _events = Channel<PlanEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onReplace(day: Weekday) {
        if (local.value.replacingDay != null) return
        local.update { it.copy(replacingDay = day) }
        viewModelScope.launch {
            val replaced = replaceMeal(day)
            local.update { it.copy(replacingDay = null) }
            if (!replaced) _events.send(PlanEvent.NoAlternative)
        }
    }

    fun onRebuildRequest() = local.update { it.copy(showRebuildDialog = true) }

    fun onRebuildDismiss() = local.update { it.copy(showRebuildDialog = false) }

    private data class LocalState(
        val replacingDay: Weekday? = null,
        val showRebuildDialog: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

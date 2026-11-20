package ch.rezeptli.app.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.plan.ObservePlanUseCase
import ch.rezeptli.app.domain.plan.PlanEntry
import ch.rezeptli.app.domain.plan.PlanPlace
import ch.rezeptli.app.domain.plan.ReplaceMealUseCase
import ch.rezeptli.app.domain.plan.StoreComparison
import ch.rezeptli.app.domain.plan.StoreEstimate
import ch.rezeptli.app.domain.profile.Currency
import ch.rezeptli.app.domain.profile.MealSlot
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

/** Die Mahlzeiten eines Tages - mittags vor abends. */
data class PlanDay(
    val day: Weekday,
    val meals: List<PlanEntry>,
)

data class PlanUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    /** Der Hauptladen - mit ihm wird gerechnet. */
    val store: StoreChain? = null,
    val currency: Currency = Currency.CHF,
    val budget: Int = UserProfile.EMPTY.budget,
    val expectedMeals: Int = UserProfile.EMPTY.mealsPerWeek,
    val meals: List<PlanEntry> = emptyList(),
    val itemsTotal: Int = 0,
    val itemsChecked: Int = 0,
    /** Was der Einkauf des Plans im Hauptladen kostet, mit ganzen Packungen. */
    val estimatedCost: Double = 0.0,
    /** Dieselbe Rechnung fuer die anderen gewaehlten Laeden - nur zum Vergleich. */
    val comparisons: List<StoreEstimate> = emptyList(),
    val replacing: PlanPlace? = null,
    val showRebuildDialog: Boolean = false,
) {
    val hasPlan: Boolean get() = meals.isNotEmpty()

    val isOverBudget: Boolean get() = estimatedCost > budget

    /** Wie weit das Budget aufgebraucht ist, fuer den Balken: zwischen 0 und 1. */
    val budgetShare: Float get() = if (budget <= 0) 0f else (estimatedCost / budget).toFloat().coerceIn(0f, 1f)

    /** Die Woche, Tag fuer Tag. */
    val days: List<PlanDay>
        get() = meals
            .groupBy { it.day }
            .map { (day, entries) -> PlanDay(day, entries.sortedBy { it.slot.ordinal }) }
            .sortedBy { it.day.ordinal }

    /** Sobald auch Mittagessen geplant sind, braucht jede Karte ihre Tageszeit. */
    val showsSlots: Boolean get() = meals.any { it.slot == MealSlot.MITTAG }

    val cookedCount: Int get() = meals.count { it.isCooked }
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
        val cost = planItems.sumOf { it.price ?: 0.0 }
        PlanUiState(
            isLoading = false,
            name = profile.firstName,
            store = profile.store,
            currency = profile.market.currency,
            budget = profile.budget,
            expectedMeals = profile.mealsPerWeek,
            meals = meals,
            itemsTotal = items.size,
            itemsChecked = items.count { it.isChecked },
            estimatedCost = cost,
            comparisons = StoreComparison.of(profile, cost),
            replacing = state.replacing,
            showRebuildDialog = state.showRebuildDialog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlanUiState(),
    )

    private val _events = Channel<PlanEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onReplace(day: Weekday, slot: MealSlot) {
        if (local.value.replacing != null) return
        local.update { it.copy(replacing = PlanPlace(day, slot)) }
        viewModelScope.launch {
            val replaced = replaceMeal(day, slot)
            local.update { it.copy(replacing = null) }
            if (!replaced) _events.send(PlanEvent.NoAlternative)
        }
    }

    fun onRebuildRequest() = local.update { it.copy(showRebuildDialog = true) }

    fun onRebuildDismiss() = local.update { it.copy(showRebuildDialog = false) }

    private data class LocalState(
        val replacing: PlanPlace? = null,
        val showRebuildDialog: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

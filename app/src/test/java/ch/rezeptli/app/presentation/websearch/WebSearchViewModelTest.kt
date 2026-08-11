package ch.rezeptli.app.presentation.websearch

import ch.rezeptli.app.domain.model.WebSearchResult
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.ranking.SourceRankingService
import ch.rezeptli.app.domain.usecase.SearchWebRecipesUseCase
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.fake.FakeWebRecipeRepository
import ch.rezeptli.app.util.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class WebSearchViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val treffer = listOf(
        result("Risotto", "https://a.ch/1", "cookaround", Country.ITALIEN),
        result("Risotto Milanese", "https://b.ch/2", "swissmilk", Country.SCHWEIZ),
        result("Risottoreis", "https://c.ch/3", "einfachkochen", Country.DEUTSCHLAND),
        result("Risotto Verde", "https://d.ch/4", "ichkoche", Country.OESTERREICH),
    )

    private val webRepository = FakeWebRecipeRepository(results = treffer)
    private val profileRepository = FakeUserProfileRepository(UserProfile(country = Country.SCHWEIZ))

    private fun createViewModel() = WebSearchViewModel(
        SearchWebRecipesUseCase(webRepository, profileRepository, SourceRankingService()),
    )

    private fun result(title: String, url: String, sourceId: String, country: Country) = WebSearchResult(
        title = title,
        url = url,
        sourceId = sourceId,
        sourceName = sourceId,
        country = country,
    )

    @Test
    fun `waermt die voreingestellten Quellen beim Oeffnen vor`() = runTest {
        createViewModel()
        advanceUntilIdle()

        assertEquals(
            WebSearchUiState.DEFAULT_SOURCES,
            webRepository.warmedUpSources,
            "Die Verzeichnisse muessen geladen werden, bevor jemand sucht",
        )
    }

    @Test
    fun `zeigt Zwischenstaende an, statt auf die letzte Quelle zu warten`() = runTest {
        val viewModel = createViewModel()
        viewModel.onQueryChange("Risotto")

        viewModel.onSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertEquals(treffer.size, state.results.size)
        assertEquals(state.totalSources, state.finishedSources)
    }

    @Test
    fun `sortiert die Treffer nach dem Wohnland`() = runTest {
        val viewModel = createViewModel()
        viewModel.onQueryChange("Risotto")

        viewModel.onSearch()
        advanceUntilIdle()

        assertEquals(
            "swissmilk",
            viewModel.uiState.value.results
                .first()
                .sourceId,
        )
    }

    @Test
    fun `meldet erst nach der letzten Quelle, dass nichts gefunden wurde`() = runTest {
        val leer = FakeWebRecipeRepository(results = emptyList())
        val viewModel = WebSearchViewModel(
            SearchWebRecipesUseCase(leer, profileRepository, SourceRankingService()),
        )
        viewModel.onQueryChange("Gibtsnicht")

        viewModel.onSearch()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmptyResult)
    }

    @Test
    fun `sucht nicht ohne Suchbegriff`() = runTest {
        val viewModel = createViewModel()

        viewModel.onSearch()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasSearched)
    }

    @Test
    fun `eine neue Suche verwirft die Treffer der alten`() = runTest {
        val viewModel = createViewModel()
        viewModel.onQueryChange("Risotto")
        viewModel.onSearch()
        advanceUntilIdle()
        assertTrue(
            viewModel.uiState.value.results
                .isNotEmpty(),
        )

        viewModel.onQueryChange("Anderes")
        viewModel.onSearch()

        // Direkt nach dem Start darf nichts Altes mehr stehen.
        assertTrue(
            viewModel.uiState.value.results
                .isEmpty(),
        )
    }
}

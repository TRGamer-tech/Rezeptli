package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.fake.FakePairingRepository
import ch.rezeptli.app.fake.FakeRecipeRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Deckt die Faelle ab, an denen "Jemanden einladen" auf dem Geraet gescheitert ist.
 */
class StartSharedSessionUseCaseTest {
    private val pairing = FakePairingRepository()

    private fun useCase(recipes: List<Recipe>) =
        StartSharedSessionUseCase(pairing, FakeRecipeRepository(recipes))

    @Test
    fun `teilt hoechstens so viele Rezepte wie der Dienst annimmt`() = runTest {
        val viele = (1L..250L).map { Recipe(id = it, title = "Rezept $it") }

        val result = useCase(viele)(RecipeFilter())

        assertTrue(result is PairingResult.Success)
        assertEquals(
            StartSharedSessionUseCase.MAX_GETEILTE,
            pairing.createdSession?.recipes?.size,
        )
    }

    @Test
    fun `schickt eigene Fotos nicht mit`() = runTest {
        val eigenes = Recipe(
            id = 1L,
            title = "Rösti",
            photoUri = "/data/user/0/ch.rezeptli.app/files/fotos/roesti.jpg",
        )

        useCase(listOf(eigenes))(RecipeFilter())

        val geteilt = pairing.createdSession?.recipes?.single()
        assertNull(geteilt?.imageUrl)
    }

    @Test
    fun `schickt Bilder aus dem Netz mit`() = runTest {
        val importiert = Recipe(
            id = 1L,
            title = "Risotto",
            photoUri = "https://www.gutekueche.ch/bilder/risotto.jpg",
        )

        useCase(listOf(importiert))(RecipeFilter())

        val geteilt = pairing.createdSession?.recipes?.single()
        assertEquals("https://www.gutekueche.ch/bilder/risotto.jpg", geteilt?.imageUrl)
    }

    @Test
    fun `meldet eine leere Sammlung als NO_RECIPES statt es zu versuchen`() = runTest {
        val result = useCase(emptyList())(RecipeFilter())

        assertEquals(PairingResult.Failure(PairingError.NO_RECIPES), result)
        assertNull(pairing.createdSession)
    }

    @Test
    fun `gibt Titel und Zeit weiter, sonst nichts`() = runTest {
        val rezept = Recipe(
            id = 7L,
            title = "Älplermagronen",
            prepTimeMinutes = 35,
            instructions = "Bleibt auf dem Geraet",
        )

        useCase(listOf(rezept))(RecipeFilter())

        val geteilt = pairing.createdSession?.recipes?.single()
        assertEquals(7L, geteilt?.recipeId)
        assertEquals("Älplermagronen", geteilt?.title)
        assertEquals(35, geteilt?.prepTimeMinutes)
    }
}

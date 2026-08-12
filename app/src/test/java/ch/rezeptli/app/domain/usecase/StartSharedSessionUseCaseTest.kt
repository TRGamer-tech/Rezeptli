package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.fake.FakePairingRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StartSharedSessionUseCaseTest {
    private val pairing = FakePairingRepository()
    private val useCase = StartSharedSessionUseCase(pairing)

    @Test
    fun `teilt hoechstens so viele Rezepte wie der Dienst annimmt`() = runTest {
        val viele = (1L..250L).map { SharedRecipe(recipeId = it, title = "Rezept $it") }

        val result = useCase(viele)

        assertTrue(result is PairingResult.Success)
        assertEquals(
            StartSharedSessionUseCase.MAX_GETEILTE,
            pairing.createdSession?.recipes?.size,
        )
    }

    @Test
    fun `meldet eine leere Auswahl als NO_RECIPES statt es zu versuchen`() = runTest {
        val result = useCase(emptyList())

        assertEquals(PairingResult.Failure(PairingError.NO_RECIPES), result)
        assertNull(pairing.createdSession)
    }
}

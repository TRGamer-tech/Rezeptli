package ch.rezeptli.app.swipe

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.swipe.SwipeCardStack
import ch.rezeptli.app.presentation.swipe.SwipeCardStackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Der Swipe-Stapel ist die zentrale Interaktion der App - deshalb wird sie hier direkt
 * ueber echte Gesten geprueft und nicht nur ueber die Logik dahinter.
 */
@RunWith(AndroidJUnit4::class)
class SwipeCardStackTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val cards = listOf(
        RecipeSummary(id = 1L, title = "Älplermagronen"),
        RecipeSummary(id = 2L, title = "Rösti"),
    )

    private val cardStackState = SwipeCardStackState()
    private var swipedRecipeId: Long? = null
    private var swipedLiked: Boolean? = null

    private fun setContent() {
        composeTestRule.setContent {
            RezeptliTheme {
                SwipeCardStack(
                    cards = cards,
                    onSwiped = { recipe, liked ->
                        swipedRecipeId = recipe.id
                        swipedLiked = liked
                    },
                    state = cardStackState,
                )
            }
        }
    }

    @Test
    fun wischenNachRechtsBedeutetJa() {
        setContent()

        composeTestRule.onNodeWithText(TOP_CARD_TITLE).performTouchInput { swipeRight() }
        composeTestRule.waitUntil(TIMEOUT_MS) { swipedRecipeId != null }

        assertEquals(1L, swipedRecipeId)
        assertEquals(true, swipedLiked)
    }

    @Test
    fun wischenNachLinksBedeutetNein() {
        setContent()

        composeTestRule.onNodeWithText(TOP_CARD_TITLE).performTouchInput { swipeLeft() }
        composeTestRule.waitUntil(TIMEOUT_MS) { swipedRecipeId != null }

        assertEquals(1L, swipedRecipeId)
        assertEquals(false, swipedLiked)
    }

    @Test
    fun nurDieObersteKarteReagiert() {
        setContent()

        composeTestRule.onNodeWithText(TOP_CARD_TITLE).performTouchInput { swipeRight() }
        composeTestRule.waitUntil(TIMEOUT_MS) { swipedRecipeId != null }

        assertEquals(1L, swipedRecipeId)
    }

    @Test
    fun ohneGesteWirdNichtsEntschieden() {
        setContent()
        composeTestRule.waitForIdle()

        assertNull(swipedRecipeId)
    }

    @Test
    fun dieSchaltflaechenSchickenDieObersteKarteWeg() {
        setContent()

        composeTestRule.runOnUiThread { cardStackState.swipe(true) }
        composeTestRule.waitUntil(TIMEOUT_MS) { swipedRecipeId != null }

        assertEquals(1L, swipedRecipeId)
        assertEquals(true, swipedLiked)
    }

    private companion object {
        const val TOP_CARD_TITLE = "Älplermagronen"
        const val TIMEOUT_MS = 5_000L
    }
}

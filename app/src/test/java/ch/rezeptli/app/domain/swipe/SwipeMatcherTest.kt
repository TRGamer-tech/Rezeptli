package ch.rezeptli.app.domain.swipe

import ch.rezeptli.app.domain.model.SwipeDecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SwipeMatcherTest {

    private val matcher = SwipeMatcher()

    private fun decision(
        recipeId: Long,
        liked: Boolean,
        participant: String = SwipeDecision.LOCAL_PARTICIPANT,
        at: Long = recipeId,
    ) = SwipeDecision(
        sessionId = 1L,
        recipeId = recipeId,
        liked = liked,
        decidedAt = at,
        participantId = participant,
    )

    @Test
    fun `liefert im Solo-Modus alle Ja-Rezepte in Entscheidungsreihenfolge`() {
        val decisions = listOf(
            decision(3, liked = true, at = 10),
            decision(1, liked = false, at = 20),
            decision(2, liked = true, at = 30),
        )

        assertEquals(listOf(3L, 2L), matcher.likedRecipeIds(decisions))
        assertEquals(listOf(3L, 2L), matcher.commonMatches(decisions))
    }

    @Test
    fun `liefert keine Treffer wenn nichts geliked wurde`() {
        val decisions = listOf(decision(1, liked = false), decision(2, liked = false))

        assertTrue(matcher.commonMatches(decisions).isEmpty())
    }

    @Test
    fun `liefert bei zwei Personen nur die gemeinsamen Treffer`() {
        val decisions = listOf(
            decision(1, liked = true, participant = "a", at = 1),
            decision(2, liked = true, participant = "a", at = 2),
            decision(3, liked = false, participant = "a", at = 3),
            decision(1, liked = true, participant = "b", at = 4),
            decision(2, liked = false, participant = "b", at = 5),
            decision(3, liked = true, participant = "b", at = 6),
        )

        assertEquals(listOf(1L), matcher.commonMatches(decisions))
    }

    @Test
    fun `wertet ein Rezept nicht als Treffer solange nicht alle entschieden haben`() {
        val decisions = listOf(
            decision(1, liked = true, participant = "a", at = 1),
            decision(2, liked = true, participant = "a", at = 2),
            decision(1, liked = true, participant = "b", at = 3),
        )

        assertEquals(listOf(1L), matcher.commonMatches(decisions))
    }

    @Test
    fun `erkennt alle beteiligten Personen`() {
        val decisions = listOf(
            decision(1, liked = true, participant = "a"),
            decision(1, liked = true, participant = "b"),
            decision(2, liked = true, participant = "a"),
        )

        assertEquals(setOf("a", "b"), matcher.participants(decisions))
    }

    @Test
    fun `liefert die noch offenen Rezepte einer unterbrochenen Session`() {
        val all = listOf(1L, 2L, 3L, 4L)
        val decisions = listOf(decision(1, liked = true), decision(3, liked = false))

        assertEquals(listOf(2L, 4L), matcher.remainingRecipeIds(all, decisions))
    }

    @Test
    fun `zaehlt Entscheidungen anderer Personen nicht als eigenen Fortschritt`() {
        val all = listOf(1L, 2L)
        val decisions = listOf(decision(1, liked = true, participant = "b"))

        assertEquals(listOf(1L, 2L), matcher.remainingRecipeIds(all, decisions, participantId = "a"))
    }

    @Test
    fun `kommt mit einer leeren Entscheidungsliste zurecht`() {
        assertTrue(matcher.commonMatches(emptyList()).isEmpty())
        assertTrue(matcher.likedRecipeIds(emptyList()).isEmpty())
        assertTrue(matcher.participants(emptyList()).isEmpty())
    }
}

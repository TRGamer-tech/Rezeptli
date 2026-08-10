package ch.rezeptli.app.domain.swipe

import ch.rezeptli.app.domain.model.SwipeDecision
import javax.inject.Inject

/**
 * Reine Auswertungslogik einer Swipe-Session.
 *
 * Im Solo-Modus gibt es genau eine teilnehmende Person, dann ist ein "Treffer"
 * schlicht ein "Ja". Die Logik ist aber bereits mehrspielerfaehig formuliert: Ein
 * Treffer ist ein Rezept, dem alle beteiligten Personen zugestimmt haben. Der spaetere
 * Mehrspieler-Modus muss damit nur noch Entscheidungen zusammenfuehren - die
 * Auswertung bleibt unveraendert.
 */
class SwipeMatcher @Inject constructor() {
    /** Alle Personen, von denen in [decisions] Entscheidungen vorliegen. */
    fun participants(decisions: List<SwipeDecision>): Set<String> =
        decisions.mapTo(linkedSetOf()) { it.participantId }

    /** Die von [participantId] mit "Ja" bewerteten Rezepte, in Reihenfolge der Entscheidung. */
    fun likedRecipeIds(
        decisions: List<SwipeDecision>,
        participantId: String = SwipeDecision.LOCAL_PARTICIPANT,
    ): List<Long> =
        decisions
            .asSequence()
            .filter { it.participantId == participantId && it.liked }
            .sortedBy { it.decidedAt }
            .map { it.recipeId }
            .distinct()
            .toList()

    /**
     * Rezepte, denen *alle* beteiligten Personen zugestimmt haben.
     *
     * Solange nur eine Person entschieden hat, entspricht das Ergebnis deren "Ja"-Liste.
     * Rezepte, die noch nicht von allen bewertet wurden, sind kein Treffer.
     */
    fun commonMatches(decisions: List<SwipeDecision>): List<Long> {
        if (decisions.isEmpty()) return emptyList()
        val allParticipants = participants(decisions)

        val byRecipe = LinkedHashMap<Long, MutableSet<String>>()
        val rejected = mutableSetOf<Long>()
        val firstDecisionAt = HashMap<Long, Long>()

        for (decision in decisions.sortedBy { it.decidedAt }) {
            firstDecisionAt.putIfAbsent(decision.recipeId, decision.decidedAt)
            if (decision.liked) {
                byRecipe.getOrPut(decision.recipeId) { mutableSetOf() }.add(decision.participantId)
            } else {
                rejected.add(decision.recipeId)
            }
        }

        return byRecipe
            .filterKeys { it !in rejected }
            .filterValues { it.containsAll(allParticipants) }
            .keys
            .sortedBy { firstDecisionAt[it] ?: 0L }
            .toList()
    }

    /**
     * Rezepte aus [allRecipeIds], zu denen [participantId] noch nicht entschieden hat.
     * Damit laesst sich eine unterbrochene Session genau dort fortsetzen, wo sie endete.
     */
    fun remainingRecipeIds(
        allRecipeIds: List<Long>,
        decisions: List<SwipeDecision>,
        participantId: String = SwipeDecision.LOCAL_PARTICIPANT,
    ): List<Long> {
        val decided = decisions
            .filter { it.participantId == participantId }
            .mapTo(mutableSetOf()) { it.recipeId }
        return allRecipeIds.filterNot { it in decided }
    }
}

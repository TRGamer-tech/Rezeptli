package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday

/**
 * Welches Gericht auf welchen Platz der Woche kommt - von Hand verteilt, bevor es den Plan ersetzt.
 *
 * Gerichte sind hier bloss Nummern: Wer verteilt, kennt von einem Treffer erst Titel und Bild.
 *
 * Zwei Gerichte stehen nie auf demselben Platz. Wer ein Gericht auf einen besetzten Platz
 * legt, tauscht: Das andere rueckt dorthin, wo das erste herkam. So geht beim Umstellen
 * nichts verloren, und es braucht dafuer keine Fehlermeldung.
 */
data class WeekAssignment(
    /** Zwischen welchen Mahlzeiten gewaehlt werden kann. Bei nur einer steht bloss der Tag zur Wahl. */
    val slots: List<MealSlot>,
    /** Gericht zu Platz. Wer fehlt, kommt nicht in den Plan. */
    val places: Map<Long, PlanPlace> = emptyMap(),
) {
    val plannedCount: Int get() = places.size

    /** Muss neben dem Tag auch die Mahlzeit gewaehlt werden? */
    val asksForSlot: Boolean get() = slots.size > 1

    fun placeOf(dish: Long): PlanPlace? = places[dish]

    /**
     * Legt ein Gericht auf einen Tag. Derselbe Tag noch einmal nimmt es aus dem Plan - so
     * braucht es neben den sieben Tagen keinen achten Knopf fuer "gar nicht".
     *
     * Die Mahlzeit bleibt beim Wechseln des Tages dieselbe. Ein Gericht, das noch keinen Platz
     * hatte, nimmt die Mahlzeit, die an dem Tag noch frei ist.
     */
    fun toggleDay(dish: Long, day: Weekday): WeekAssignment {
        val current = places[dish]
        if (current?.day == day) return copy(places = places - dish)
        val slot = current?.slot ?: freeSlotOn(day) ?: preferred.first()
        return put(dish, PlanPlace(day, slot))
    }

    /** Mittag oder Abend - fuer ein Gericht, das seinen Tag schon hat. */
    fun withSlot(dish: Long, slot: MealSlot): WeekAssignment {
        val current = places[dish] ?: return this
        if (slot !in slots) return this
        return put(dish, current.copy(slot = slot))
    }

    private fun put(dish: Long, place: PlanPlace): WeekAssignment {
        val before = places[dish]
        if (before == place) return this

        val next = places.toMutableMap()
        val other = places.entries.firstOrNull { it.value == place }?.key
        if (other != null) {
            // Tauschen. Kam das erste Gericht von nirgends, hat das andere keinen Platz mehr.
            if (before != null) next[other] = before else next.remove(other)
        }
        next[dish] = place
        return copy(places = next)
    }

    private fun freeSlotOn(day: Weekday): MealSlot? {
        val taken = places.values.filter { it.day == day }.mapTo(mutableSetOf()) { it.slot }
        return preferred.firstOrNull { it !in taken }
    }

    /** Das Abendessen zuerst - es ist die Mahlzeit, fuer die am ehesten gekocht wird. */
    private val preferred: List<MealSlot> get() = slots.sortedByDescending { it == MealSlot.ABEND }

    companion object {
        private const val DAYS = 7

        /**
         * Ein Vorschlag, mit dem man meist schon einverstanden ist.
         *
         * Die Gerichte kommen der Reihe nach auf die Kochtage - ab [today], denn was heute
         * verteilt wird, soll zuerst die naechsten Tage fuellen und nicht den Montag, der
         * schon vorbei ist. Reichen die Kochtage nicht, folgen die uebrigen Tage. Reicht auch
         * das nicht (mehr Gerichte als Tage), kommt die Mahlzeit dazu, die sonst nicht geplant
         * wird. Was dann noch uebrig ist, bleibt ohne Platz.
         */
        fun suggest(dishes: List<Long>, profile: UserProfile, today: Weekday): WeekAssignment {
            val own = profile.slots
            val slots = if (dishes.size > DAYS * own.size) MealSlot.entries.toList() else own
            val ordered = slots.sortedByDescending { it == MealSlot.ABEND }
            val usual = ordered.filter { it in own }
            val extra = ordered - usual.toSet()

            fun fromToday(days: List<Weekday>) = days.sortedBy { (it.ordinal - today.ordinal + DAYS) % DAYS }
            val cookDays = fromToday(profile.days)
            val otherDays = fromToday(Weekday.entries - profile.days.toSet())

            fun places(slots: List<MealSlot>, days: List<Weekday>) =
                slots.flatMap { slot -> days.map { day -> PlanPlace(day, slot) } }
            val order = places(usual, cookDays) + places(usual, otherDays) + places(extra, cookDays + otherDays)

            return WeekAssignment(
                slots = slots.sortedBy { it.ordinal },
                places = dishes.distinct().zip(order).toMap(),
            )
        }
    }
}

package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Die Tageswahl von "Woche ueberschreiben": Welcher Treffer kommt an welchem Tag auf den Tisch?
 *
 * Gewuenscht war, dass sich das fuer jedes Gericht waehlen laesst. Geprueft wird der Vorschlag,
 * mit dem das Blatt aufgeht, und dass beim Umstellen nie zwei Gerichte auf demselben Platz
 * landen und keines unbemerkt verschwindet.
 */
class WeekAssignmentTest {
    private val nurAbends = UserProfile(
        cookDays = Weekday.entries.take(5).toSet(),
        mealSlots = setOf(MealSlot.ABEND),
    )
    private val mittagUndAbend = nurAbends.copy(mealSlots = MealSlot.entries.toSet())

    private fun abend(tag: Weekday) = PlanPlace(tag, MealSlot.ABEND)

    private fun mittag(tag: Weekday) = PlanPlace(tag, MealSlot.MITTAG)

    private fun WeekAssignment.tage(vararg gerichte: Long) = gerichte.map { placeOf(it)?.day }

    // ------------------------------------------------------------------ der Vorschlag

    @Test
    fun `vorgeschlagen werden die Kochtage ab heute - nicht der Montag, der schon vorbei ist`() {
        val woche = WeekAssignment.suggest(listOf(1L, 2L, 3L), nurAbends, today = Weekday.DONNERSTAG)

        assertEquals(listOf(Weekday.DONNERSTAG, Weekday.FREITAG, Weekday.MONTAG), woche.tage(1L, 2L, 3L))
        assertTrue(woche.places.values.all { it.slot == MealSlot.ABEND })
        // Nur das Abendessen wird geplant - also steht auch nur der Tag zur Wahl.
        assertFalse(woche.asksForSlot)
    }

    @Test
    fun `ist heute kein Kochtag, beginnt der Vorschlag am naechsten`() {
        val woche = WeekAssignment.suggest(listOf(1L, 2L), nurAbends, today = Weekday.SAMSTAG)

        assertEquals(listOf(Weekday.MONTAG, Weekday.DIENSTAG), woche.tage(1L, 2L))
    }

    @Test
    fun `reichen die Kochtage nicht, kommen die uebrigen Tage dazu`() {
        val woche = WeekAssignment.suggest((1L..7L).toList(), nurAbends, today = Weekday.MONTAG)

        assertEquals(Weekday.entries.take(5), woche.tage(1L, 2L, 3L, 4L, 5L))
        assertEquals(listOf(Weekday.SAMSTAG, Weekday.SONNTAG), woche.tage(6L, 7L))
        assertFalse(woche.asksForSlot)
    }

    @Test
    fun `mehr Gerichte als Tage - dann kommt auch das Mittagessen dazu`() {
        val woche = WeekAssignment.suggest((1L..9L).toList(), nurAbends, today = Weekday.MONTAG)

        assertTrue(woche.asksForSlot)
        assertEquals(7, woche.places.values.count { it.slot == MealSlot.ABEND })
        assertEquals(
            listOf(mittag(Weekday.MONTAG), mittag(Weekday.DIENSTAG)),
            listOf(woche.placeOf(8L), woche.placeOf(9L)),
        )
        assertEquals(
            9,
            woche.places.values
                .distinct()
                .size,
        )
    }

    @Test
    fun `was auch dann keinen Platz findet, bleibt ohne Tag`() {
        val woche = WeekAssignment.suggest((1L..16L).toList(), mittagUndAbend, today = Weekday.MITTWOCH)

        assertEquals(14, woche.plannedCount)
        assertEquals(
            14,
            woche.places.values
                .distinct()
                .size,
        )
        assertNull(woche.placeOf(15L))
        assertNull(woche.placeOf(16L))
    }

    @Test
    fun `wer mittags und abends plant, bekommt zuerst die Abende gefuellt`() {
        val woche = WeekAssignment.suggest((1L..6L).toList(), mittagUndAbend, today = Weekday.MONTAG)

        assertTrue(woche.asksForSlot)
        assertEquals(Weekday.entries.take(5).map(::abend), (1L..5L).map { woche.placeOf(it) })
        assertEquals(mittag(Weekday.MONTAG), woche.placeOf(6L))
    }

    @Test
    fun `ohne gewaehlte Tage und Mahlzeiten gilt, was der Plan auch sonst annimmt`() {
        val woche = WeekAssignment.suggest(listOf(1L), UserProfile.EMPTY, today = Weekday.DIENSTAG)

        assertEquals(PlanPlace(Weekday.DIENSTAG, MealSlot.ABEND), woche.placeOf(1L))
    }

    // ------------------------------------------------------------------ umstellen

    private val drei = WeekAssignment.suggest(listOf(1L, 2L, 3L), nurAbends, today = Weekday.MONTAG)

    @Test
    fun `ein Gericht zieht auf einen freien Tag`() {
        val woche = drei.toggleDay(1L, Weekday.SAMSTAG)

        assertEquals(listOf(Weekday.SAMSTAG, Weekday.DIENSTAG, Weekday.MITTWOCH), woche.tage(1L, 2L, 3L))
    }

    @Test
    fun `auf einem vergebenen Tag tauschen die beiden die Plaetze`() {
        val woche = drei.toggleDay(1L, Weekday.MITTWOCH)

        assertEquals(listOf(Weekday.MITTWOCH, Weekday.DIENSTAG, Weekday.MONTAG), woche.tage(1L, 2L, 3L))
    }

    @Test
    fun `derselbe Tag noch einmal nimmt das Gericht aus dem Plan`() {
        val woche = drei.toggleDay(2L, Weekday.DIENSTAG)

        assertNull(woche.placeOf(2L))
        assertEquals(2, woche.plannedCount)
        // ... und ein Tipp auf einen Tag holt es zurueck.
        assertEquals(abend(Weekday.FREITAG), woche.toggleDay(2L, Weekday.FREITAG).placeOf(2L))
    }

    @Test
    fun `ein Gericht ohne Tag verdraengt, was auf dem gewaehlten Tag steht`() {
        val ohneZwei = drei.toggleDay(2L, Weekday.DIENSTAG)

        val woche = ohneZwei.toggleDay(2L, Weekday.MONTAG)

        // Tauschen geht nicht - das zweite Gericht kam von nirgends. Also steht das erste jetzt ohne Tag da.
        assertEquals(abend(Weekday.MONTAG), woche.placeOf(2L))
        assertNull(woche.placeOf(1L))
        assertEquals(2, woche.plannedCount)
    }

    @Test
    fun `der Tageswechsel behaelt die Mahlzeit`() {
        val woche = WeekAssignment(slots = MealSlot.entries, places = mapOf(1L to mittag(Weekday.MONTAG)))

        assertEquals(mittag(Weekday.FREITAG), woche.toggleDay(1L, Weekday.FREITAG).placeOf(1L))
    }

    @Test
    fun `ein Gericht ohne Tag nimmt die Mahlzeit, die an dem Tag noch frei ist`() {
        val woche = WeekAssignment(slots = MealSlot.entries, places = mapOf(1L to abend(Weekday.MONTAG)))

        val mitZwei = woche.toggleDay(2L, Weekday.MONTAG)

        assertEquals(abend(Weekday.MONTAG), mitZwei.placeOf(1L))
        assertEquals(mittag(Weekday.MONTAG), mitZwei.placeOf(2L))
    }

    @Test
    fun `Mittag und Abend desselben Tages tauschen genauso`() {
        val woche = WeekAssignment(
            slots = MealSlot.entries,
            places = mapOf(1L to abend(Weekday.MONTAG), 2L to mittag(Weekday.MONTAG)),
        )

        val getauscht = woche.withSlot(1L, MealSlot.MITTAG)

        assertEquals(mittag(Weekday.MONTAG), getauscht.placeOf(1L))
        assertEquals(abend(Weekday.MONTAG), getauscht.placeOf(2L))
    }

    @Test
    fun `eine Mahlzeit, die nicht zur Wahl steht, aendert nichts - ebenso wenig ohne Tag`() {
        assertEquals(drei, drei.withSlot(1L, MealSlot.MITTAG))

        val woche = WeekAssignment(slots = MealSlot.entries)
        assertEquals(woche, woche.withSlot(1L, MealSlot.MITTAG))
    }

    @Test
    fun `wie auch immer umgestellt wird - nie stehen zwei Gerichte auf demselben Platz`() {
        var woche = WeekAssignment.suggest((1L..10L).toList(), mittagUndAbend, today = Weekday.FREITAG)
        val zuege = listOf(
            3L to Weekday.MONTAG,
            7L to Weekday.MONTAG,
            1L to Weekday.SONNTAG,
            3L to Weekday.MONTAG,
            9L to Weekday.FREITAG,
            3L to Weekday.FREITAG,
            10L to Weekday.FREITAG,
            2L to Weekday.SAMSTAG,
        )

        zuege.forEachIndexed { index, (gericht, tag) ->
            woche = woche.toggleDay(gericht, tag)
            if (index % 2 == 0) woche = woche.withSlot(gericht, MealSlot.entries[index % 4 / 2])
            assertEquals(
                woche.places.size,
                woche.places.values
                    .distinct()
                    .size,
                "nach Zug ${index + 1}",
            )
        }
    }
}

package ch.rezeptli.app.domain.deck

import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.ranking.SourceOrigin
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Ein Rezept, wie es aus dem Verzeichnis kommt - noch ungeladen.
 *
 * Mehr als das weiss das Verzeichnis nicht. Zutaten und Zubereitung holt die App erst,
 * wenn jemand das Rezept behalten will.
 */
data class DeckEntry(
    val url: String,
    val title: String,
    val imageUrl: String? = null,
    val sourceId: String,
    val sourceName: String,
)

/**
 * Stellt den Stapel zusammen, mit dem die App startet.
 *
 * Gezogen wird zufaellig, aber nicht wahllos: Quellen aus dem eigenen Land und dem
 * eigenen Sprachraum bilden den Grundstock, dazu kommt eine feste kleine Beimischung
 * aus anderen Sprachraeumen - sonst sieht ein Schweizer Haushalt nie ein italienisches
 * Rezept, was der Sinn der Sache waere.
 *
 * Wer Kuechen angegeben hat, bekommt mehr davon: Rund die Haelfte des Stapels kommt aus
 * den gewaehlten Kuechen, Gerichte einer klar anderen Kueche tauchen nur noch ab und zu
 * auf. Es ist eine Vorliebe, kein Filter - und was zu keiner Kueche gehoert, bleibt wie es war.
 *
 * Innerhalb dieser Gruppen ist die Ziehung gleichverteilt. Nichts wird nach Beliebtheit
 * gewichtet, denn Beliebtheit wuesste die App nur, wenn sie das Verhalten ihrer Nutzer
 * auswerten wuerde - und das tut sie nicht.
 */
class CuratedDeckBuilder @Inject constructor() {
    /**
     * [entriesBySource] sind die Verzeichnisse je Quelle, [origins] deren Herkunft.
     *
     * [random] ist einsetzbar, damit sich die Ziehung testen laesst - ohne das waere
     * "zufaellig" nicht pruefbar. [cuisinesOf] nennt die Kuechen, die sich einem Eintrag
     * ansehen lassen; ohne das zaehlt nur der Sprachraum der Quelle.
     */
    fun build(
        entriesBySource: Map<String, List<DeckEntry>>,
        origins: Map<String, SourceOrigin>,
        profile: UserProfile,
        size: Int = DEFAULT_SIZE,
        exclude: Set<String> = emptySet(),
        random: Random = Random.Default,
        cuisinesOf: (DeckEntry) -> Set<Cuisine> = { emptySet() },
    ): List<DeckEntry> {
        if (size <= 0) return emptyList()

        val ownRegion = profile.country?.region
        val wantedForeign = profile.cuisines.mapNotNullTo(mutableSetOf()) { it.region } - setOfNotNull(ownRegion)

        val verfuegbar = entriesBySource
            .mapValues { (_, eintraege) -> eintraege.filterNot { it.url in exclude } }
            .filterValues { it.isNotEmpty() }
        if (verfuegbar.isEmpty()) return emptyList()

        val (nah, fern) = verfuegbar.entries.partition { (quelle, _) ->
            val region = origins[quelle]?.region
            region == null || ownRegion == null || region == ownRegion
        }

        // Wurde im Onboarding eine fremde Kueche angegeben, kommt die Beimischung genau
        // aus deren Sprachraum - sonst waere die Angabe folgenlos.
        val gewuenscht = fern.filter { origins[it.key]?.region in wantedForeign }
        val beimischung = gewuenscht.ifEmpty { fern }

        // Fremdsprachige Quellen sind normalerweise nur eine Prise. Wer eine solche
        // Kueche ausdruecklich mag, bekommt mehr davon.
        val fernAnteil = when {
            beimischung.isEmpty() -> 0.0
            nah.isEmpty() -> 1.0
            gewuenscht.isNotEmpty() -> INTERESTED_SHARE
            else -> BASE_SHARE
        }

        val ausFern = (size * fernAnteil).toInt().coerceAtMost(size)
        val ausNah = size - ausFern

        // Quellen aus dem Sprachraum einer gewuenschten Kueche zaehlen ganz als "gemocht":
        // Auf einer italienischen Seite ist jedes Rezept italienisch, auch ohne "Pasta" im Titel.
        val gemochteQuellen = gewuenscht.mapTo(mutableSetOf()) { it.key }
        val vorliebe = Vorliebe(profile.cuisines, gemochteQuellen, cuisinesOf)

        val gezogen = ziehe(nah, ausNah, random, vorliebe) + ziehe(beimischung, ausFern, random, vorliebe)

        // Fehlt etwas, weil eine Gruppe zu klein war, mit dem Rest auffuellen.
        val fehlend = size - gezogen.size
        val nachschub = if (fehlend > 0) {
            verfuegbar.values
                .flatten()
                .filterNot { eintrag -> gezogen.any { it.url == eintrag.url } }
                .shuffled(random)
                .take(fehlend)
        } else {
            emptyList()
        }

        return (gezogen + nachschub).shuffled(random)
    }

    /** Was jemand gern isst - und woran sich das bei einem Eintrag erkennen laesst. */
    private class Vorliebe(
        val kuechen: Set<Cuisine>,
        val gemochteQuellen: Set<String>,
        val kuechenVon: (DeckEntry) -> Set<Cuisine>,
    ) {
        val aktiv: Boolean get() = kuechen.isNotEmpty()
    }

    /**
     * Zieht [anzahl] Eintraege aus den Quellen einer Gruppe.
     *
     * Ohne Kuechen-Vorliebe gleichmaessig reihum. Mit Vorliebe in drei Toepfen: gemocht,
     * klar anders, ohne Zuordnung. Reicht ein Topf nicht, fuellen die anderen auf - eine
     * Vorliebe soll den Stapel faerben, nicht verkleinern.
     */
    private fun ziehe(
        gruppen: List<Map.Entry<String, List<DeckEntry>>>,
        anzahl: Int,
        random: Random,
        vorliebe: Vorliebe,
    ): List<DeckEntry> {
        if (anzahl <= 0 || gruppen.isEmpty()) return emptyList()
        if (!vorliebe.aktiv) return reihum(gruppen.map { it.value }, anzahl, random)

        val gemocht = mutableListOf<List<DeckEntry>>()
        val anders = mutableListOf<List<DeckEntry>>()
        val neutral = mutableListOf<List<DeckEntry>>()
        gruppen.forEach { (quelle, eintraege) ->
            val jeTopf = eintraege.groupBy { eintrag ->
                val kuechen = vorliebe.kuechenVon(eintrag)
                when {
                    quelle in vorliebe.gemochteQuellen || kuechen.any { it in vorliebe.kuechen } -> GEMOCHT
                    kuechen.isNotEmpty() -> ANDERS
                    else -> NEUTRAL
                }
            }
            jeTopf[GEMOCHT]?.let { gemocht += it }
            jeTopf[ANDERS]?.let { anders += it }
            jeTopf[NEUTRAL]?.let { neutral += it }
        }

        val ausGemocht = reihum(gemocht, (anzahl * LIKED_SHARE).roundToInt(), random)
        val ausAnderen = reihum(anders, (anzahl * OTHER_SHARE).toInt(), random)
        val ausNeutral = reihum(neutral, anzahl - ausGemocht.size - ausAnderen.size, random)
        val gezogen = ausGemocht + ausAnderen + ausNeutral

        val fehlend = anzahl - gezogen.size
        if (fehlend <= 0) return gezogen

        // Ein Topf war zu klein: erst mehr Gemochtes, dann Neutrales, zuletzt anderes.
        val schon = gezogen.mapTo(mutableSetOf()) { it.url }
        val rest = (gemocht + neutral + anders).map { topf -> topf.filterNot { it.url in schon } }
        return gezogen + nacheinander(rest, fehlend, random)
    }

    /**
     * Zieht reihum aus allen Quellen.
     *
     * Reihum, damit nicht eine grosse Quelle den ganzen Stapel fuellt: Gutekueche hat
     * 17'000 Rezepte, Bettys Kuechenschaetze 16 - bei reiner Gleichverteilung ueber alle
     * Eintraege kaeme die kleine Quelle praktisch nie vor.
     */
    private fun reihum(quellen: List<List<DeckEntry>>, anzahl: Int, random: Random): List<DeckEntry> {
        if (anzahl <= 0 || quellen.isEmpty()) return emptyList()

        val vorraete = quellen
            .map { eintraege -> eintraege.shuffled(random).toMutableList() }
            .toMutableList()

        val ergebnis = mutableListOf<DeckEntry>()
        var index = 0
        while (ergebnis.size < anzahl && vorraete.any { it.isNotEmpty() }) {
            val vorrat = vorraete[index % vorraete.size]
            if (vorrat.isNotEmpty()) ergebnis += vorrat.removeAt(vorrat.lastIndex)
            index += 1
        }
        return ergebnis
    }

    /** Leert die Toepfe der Reihe nach - fuer das Auffuellen, wo die Reihenfolge der Toepfe zaehlt. */
    private fun nacheinander(toepfe: List<List<DeckEntry>>, anzahl: Int, random: Random): List<DeckEntry> =
        toepfe.flatMap { it.shuffled(random) }.take(anzahl)

    companion object {
        /** So viele Karten, dass man eine Weile wischen kann, ohne endlos zu laden. */
        const val DEFAULT_SIZE = 30

        /** Pro gesuchtem Gericht ein paar Karten - abgelehnt wird oefter als behalten. */
        private const val CARDS_PER_TARGET = 6
        private const val MIN_DECK = 12

        /** Anteil fremdsprachiger Quellen ohne besonderes Interesse. */
        private const val BASE_SHARE = 0.15

        /** Anteil, wenn im Onboarding eine solche Kueche angegeben wurde. */
        private const val INTERESTED_SHARE = 0.4

        /** So viel des Stapels kommt aus den gewaehlten Kuechen, wenn es genug davon gibt. */
        private const val LIKED_SHARE = 0.5

        /** Gerichte einer klar anderen Kueche tauchen nur noch ab und zu auf. */
        private const val OTHER_SHARE = 0.1

        private const val GEMOCHT = 0
        private const val ANDERS = 1
        private const val NEUTRAL = 2

        /**
         * Wie gross ein Stapel fuer ein gegebenes Ziel sein sollte.
         *
         * Gemeinsam fuer den Alleingang und die gemeinsame Runde - beide ziehen aus
         * demselben Verzeichnis, und "wie viele Karten fuer wie viele Gerichte" ist
         * dieselbe Frage in beiden Faellen.
         */
        fun deckSizeFor(target: Int): Int = (target * CARDS_PER_TARGET).coerceAtLeast(MIN_DECK)
    }
}

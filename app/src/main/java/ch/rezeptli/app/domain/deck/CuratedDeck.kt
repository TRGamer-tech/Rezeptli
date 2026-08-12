package ch.rezeptli.app.domain.deck

import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.ranking.SourceOrigin
import javax.inject.Inject
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
 * Innerhalb dieser Gruppen ist die Ziehung gleichverteilt. Nichts wird nach Beliebtheit
 * gewichtet, denn Beliebtheit wuesste die App nur, wenn sie das Verhalten ihrer Nutzer
 * auswerten wuerde - und das tut sie nicht.
 */
class CuratedDeckBuilder @Inject constructor() {
    /**
     * [entriesBySource] sind die Verzeichnisse je Quelle, [origins] deren Herkunft.
     *
     * [random] ist einsetzbar, damit sich die Ziehung testen laesst - ohne das waere
     * "zufaellig" nicht pruefbar.
     */
    fun build(
        entriesBySource: Map<String, List<DeckEntry>>,
        origins: Map<String, SourceOrigin>,
        profile: UserProfile,
        size: Int = DEFAULT_SIZE,
        exclude: Set<String> = emptySet(),
        random: Random = Random.Default,
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

        val gezogen = ziehe(nah, ausNah, random) + ziehe(beimischung, ausFern, random)

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

    /**
     * Zieht [anzahl] Eintraege, reihum aus allen Quellen.
     *
     * Reihum, damit nicht eine grosse Quelle den ganzen Stapel fuellt: Gutekueche hat
     * 17'000 Rezepte, Bettys Kuechenschaetze 16 - bei reiner Gleichverteilung ueber alle
     * Eintraege kaeme die kleine Quelle praktisch nie vor.
     */
    private fun ziehe(
        gruppen: List<Map.Entry<String, List<DeckEntry>>>,
        anzahl: Int,
        random: Random,
    ): List<DeckEntry> {
        if (anzahl <= 0 || gruppen.isEmpty()) return emptyList()

        val vorraete = gruppen
            .map { (_, eintraege) -> eintraege.shuffled(random).toMutableList() }
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

    companion object {
        /** So viele Karten, dass man eine Weile wischen kann, ohne endlos zu laden. */
        const val DEFAULT_SIZE = 30

        /** Anteil fremdsprachiger Quellen ohne besonderes Interesse. */
        private const val BASE_SHARE = 0.15

        /** Anteil, wenn im Onboarding eine solche Kueche angegeben wurde. */
        private const val INTERESTED_SHARE = 0.4
    }
}

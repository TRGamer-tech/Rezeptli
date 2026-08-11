package ch.rezeptli.app.domain.ranking

import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.LanguageRegion
import ch.rezeptli.app.domain.profile.UserProfile
import javax.inject.Inject

/**
 * Woher eine Quelle stammt.
 *
 * Bewusst getrennt von der Quelle selbst: Die Gewichtung soll sich testen lassen, ohne
 * Sitemaps, Adressmuster und Netzwerkschicht mitzuschleppen.
 */
data class SourceOrigin(
    val sourceId: String,
    val country: Country,
) {
    val region: LanguageRegion get() = country.region
}

/**
 * Bringt Quellen in die Reihenfolge, die zum Profil passt.
 *
 * Die Regeln in Worten:
 *
 * - Quellen aus dem eigenen Wohnland stehen zuoberst.
 * - Danach kommen Quellen aus demselben Sprachraum. Fuer die Schweiz sind das
 *   Deutschland und Oesterreich, und umgekehrt.
 * - Franzoesische und italienische Quellen laufen immer mit, auch bei einem Profil aus
 *   dem deutschsprachigen Raum - sie sollen die Auswahl bereichern. Sie stehen aber
 *   nicht zuoberst, solange niemand nach ihnen gefragt hat.
 * - Wer im Onboarding italienische oder franzoesische Kueche angegeben hat, holt die
 *   Quellen dieses Sprachraums nach vorn - vor den gleichen Sprachraum, aber nicht vor
 *   das eigene Land.
 *
 * Ohne Angaben im Profil bekommen alle Quellen dieselbe Bewertung. Dann bleibt die
 * Reihenfolge des Katalogs erhalten, statt zufaellig zu werden.
 */
class SourceRankingService @Inject constructor() {
    /**
     * Bewertet eine Quelle. Hoehere Werte stehen weiter oben.
     *
     * Der Wert ist absichtlich nachvollziehbar zusammengesetzt statt normiert: Wer
     * wissen will, warum eine Quelle vor einer anderen steht, kann die Anteile addieren.
     */
    fun score(origin: SourceOrigin, profile: UserProfile): Int {
        val ownCountry = profile.country
        val ownRegion = ownCountry?.region

        var total = when {
            ownCountry != null && origin.country == ownCountry -> SAME_COUNTRY
            origin.region == ownRegion -> SAME_REGION
            // Fremdsprachige Quellen laufen mit, aber weiter unten.
            else -> OTHER_REGION
        }

        // Der Zuschlag gilt nur fuer fremde Sprachraeume. Wer "Schweizer Klassiker"
        // angibt, soll damit nicht deutsche Quellen ueber die eigenen heben - die
        // eigene Region steht ohnehin schon oben.
        val wantedRegions = profile.cuisines.mapNotNullTo(mutableSetOf()) { it.region }
        if (origin.region in wantedRegions && origin.region != ownRegion) {
            total += CUISINE_INTEREST
        }

        return total
    }

    /** Die Quellen selbst, nach Passung sortiert. */
    fun rank(origins: List<SourceOrigin>, profile: UserProfile): List<SourceOrigin> =
        origins.sortedByDescending { score(it, profile) }

    /**
     * Sortiert beliebige Eintraege, die einer Quelle zugeordnet sind.
     *
     * [originOf] darf `null` liefern - Eintraege ohne bekannte Herkunft landen hinten,
     * statt die Sortierung zum Absturz zu bringen.
     *
     * Die Sortierung ist stabil: Bei gleicher Bewertung bleibt die urspruengliche
     * Reihenfolge erhalten. Damit bleibt eine Trefferliste ruhig, statt bei jedem
     * Aufruf zu springen.
     */
    fun <T> rankItems(items: List<T>, profile: UserProfile, originOf: (T) -> SourceOrigin?): List<T> =
        items.sortedByDescending { item ->
            originOf(item)?.let { score(it, profile) } ?: Int.MIN_VALUE
        }

    private companion object {
        const val SAME_COUNTRY = 100
        const val SAME_REGION = 60
        const val CUISINE_INTEREST = 50
        const val OTHER_REGION = 20
    }
}

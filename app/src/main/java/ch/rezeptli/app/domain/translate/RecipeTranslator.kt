package ch.rezeptli.app.domain.translate

import ch.rezeptli.app.domain.profile.Country

/** Die Sprachen, aus denen uebersetzt wird - mehr Quellen hat die App nicht. */
enum class SourceLanguage(val code: String) {
    FRANZOESISCH("fr"),
    ITALIENISCH("it"),
    ;

    companion object {
        /**
         * Die Sprache einer Quelle, abgeleitet aus ihrem Land.
         *
         * Bewusst nicht aus dem Text geraten: Eine Spracherkennung liegt bei kurzen
         * Zutatenzeilen oft daneben, und das Land der Quelle steht ohnehin fest.
         */
        fun forCountry(country: Country?): SourceLanguage? = when (country) {
            Country.FRANKREICH -> FRANZOESISCH
            Country.ITALIEN -> ITALIENISCH
            else -> null
        }
    }
}

/**
 * Uebersetzt Rezepttexte auf dem Geraet.
 *
 * Die Umsetzung laedt dafuer ein Sprachmodell herunter - rund 30 MB je Sprachpaar,
 * einmalig und nur ueber WLAN. Bis es da ist, bleibt das Rezept in seiner Sprache:
 * Ein franzoesisches Rezept ist immer noch besser als eine Fehlermeldung.
 */
interface RecipeTranslator {
    /**
     * Uebersetzt [texts] nach Deutsch, in derselben Reihenfolge.
     *
     * `null`, wenn nicht uebersetzt werden konnte - fehlendes Modell, kein WLAN,
     * abgebrochener Download. Der Aufrufer behaelt dann das Original.
     */
    suspend fun toGerman(texts: List<String>, from: SourceLanguage): List<String>?
}

/** Tut nichts - fuer Tests und als Rueckfall, wenn Uebersetzen nicht in Frage kommt. */
object NoTranslation : RecipeTranslator {
    override suspend fun toGerman(texts: List<String>, from: SourceLanguage): List<String>? = null
}

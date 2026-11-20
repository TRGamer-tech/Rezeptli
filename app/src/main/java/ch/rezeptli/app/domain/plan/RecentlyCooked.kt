package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.deck.TitleKey
import ch.rezeptli.app.domain.deck.UrlKey
import ch.rezeptli.app.domain.model.Recipe

/**
 * Was in letzter Zeit auf dem Tisch stand.
 *
 * Der Plan schlaegt so etwas nicht gleich wieder vor: In den ersten zwei Wochen kommt ein
 * gekochtes Gericht nur, wenn es sonst nichts Passendes gibt. Danach verliert es zwei weitere
 * Wochen lang noch Punkte, bis es wieder zaehlt wie jedes andere.
 *
 * Erkannt wird ein Gericht an seiner Adresse oder an seinem Titel. So zaehlt auch dasselbe
 * Gericht aus einer anderen Quelle - und ein Rezept, das nur fuer den Plan geholt und danach
 * wieder weggeraeumt wurde: Der Vermerk am Rezept selbst waere mit ihm verschwunden.
 */
class RecentlyCooked(
    /** Erkennungszeichen (siehe [keysOf]) und der Zeitpunkt, an dem zuletzt gekocht wurde. */
    private val cookedAt: Map<String, Long>,
    private val now: Long,
) {
    /** Vor wie vielen Tagen das Gericht zuletzt gekocht wurde - `null`, wenn nie oder lange her. */
    fun daysSince(recipe: Recipe): Int? {
        val last = (keysOf(recipe).mapNotNull(cookedAt::get) + listOfNotNull(recipe.lastCookedAt)).maxOrNull()
            ?: return null
        val days = ((now - last) / DAY_MS).coerceAtLeast(0L)
        return days.takeIf { it < FADE_DAYS }?.toInt()
    }

    /** Erst vor Kurzem gekocht: Das kommt nur in den Plan, wenn sonst nichts da ist. */
    fun isRecent(recipe: Recipe): Boolean = daysSince(recipe)?.let { it < AVOID_DAYS } == true

    /**
     * Wie viele Punkte ein Gericht verliert, das vor zwei bis vier Wochen gekocht wurde:
     * erst viele, dann immer weniger. Fuer alles andere null.
     */
    fun fade(recipe: Recipe): Double {
        val days = daysSince(recipe) ?: return 0.0
        if (days < AVOID_DAYS) return FADE_POINTS
        return FADE_POINTS * (FADE_DAYS - days) / (FADE_DAYS - AVOID_DAYS)
    }

    companion object {
        /** So lange wird ein gekochtes Gericht gemieden, solange es Auswahl gibt. */
        const val AVOID_DAYS = 14

        /** Danach ist vergessen, dass es das Gericht gab. */
        const val FADE_DAYS = 28

        private const val FADE_POINTS = 4.0
        private const val DAY_MS = 24 * 60 * 60 * 1_000L

        /** Niemand hat gekocht - etwa beim allerersten Plan. */
        val NONE = RecentlyCooked(emptyMap(), 0L)

        /** Woran ein Gericht wiedererkannt wird: an seiner Adresse und an seinem Titel. */
        fun keysOf(recipe: Recipe): List<String> =
            listOfNotNull(recipe.sourceUrl?.let(UrlKey::of), TitleKey.of(recipe.title))
    }
}

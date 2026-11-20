package ch.rezeptli.app.domain.deck

import ch.rezeptli.app.domain.plan.RecipeClassifier
import ch.rezeptli.app.domain.plan.RecipeTraits
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Was sich einer Karte aus dem Verzeichnis am Titel ansehen laesst.
 *
 * Das Verzeichnis hat ein paar tausend Eintraege, und jeder Stapel fragt dieselben wieder
 * ab. Die Einschaetzung haengt nur am Titel - also wird sie einmal gemacht und behalten.
 */
@Singleton
class DeckTitles @Inject constructor(
    private val classifier: RecipeClassifier,
) {
    private val known = ConcurrentHashMap<String, RecipeTraits>()

    fun traits(entry: DeckEntry): RecipeTraits = known.getOrPut(entry.url) { classifier.classifyTitle(entry.title) }
}

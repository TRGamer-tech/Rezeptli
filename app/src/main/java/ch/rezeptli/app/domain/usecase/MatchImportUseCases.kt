package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.RecipeSharing
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.repository.PairingRepository
import ch.rezeptli.app.domain.repository.RecipeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Was beim Holen eines gemeinsamen Treffers herauskam.
 *
 * Ein Treffer ist zunaechst nur eine Karte: Titel und Bild. Damit er sich behalten, kochen
 * oder einplanen laesst, braucht es das ganze Rezept - und das kommt je nach Herkunft von
 * woanders her. Darum scheitert es auch auf verschiedene Arten, und die App soll sagen
 * koennen, auf welche.
 */
sealed interface FetchedMatch {
    val match: SharedRecipe

    /**
     * Das Rezept liegt vor. [localId] nennt die Nummer, unter der es schon auf diesem Geraet
     * gespeichert ist - `null`, wenn es frisch geholt wurde und noch nirgends liegt.
     */
    data class Ready(
        override val match: SharedRecipe,
        val recipe: Recipe,
        val localId: Long? = null,
    ) : FetchedMatch

    /** Die Rezeptseite liess sich nicht laden - kein Netz, oder die Seite antwortet nicht. */
    data class NotLoaded(override val match: SharedRecipe) : FetchedMatch

    /**
     * Ein selbst erfasstes Rezept der anderen Person, dessen Inhalt ihr Telefon noch nicht
     * hinterlegt hat. Das holt sich nach, sobald sie die Runde wieder offen hat.
     */
    data class NotYetShared(override val match: SharedRecipe) : FetchedMatch
}

/**
 * Holt die gemeinsamen Treffer als ganze Rezepte.
 *
 * Drei Wege, in dieser Reihenfolge: Liegt das Rezept schon auf diesem Geraet, ist es sofort
 * da. Hat es eine Adresse, wird es von seiner Seite geladen - mit einem zweiten Versuch,
 * weil ein einzelner Aussetzer im Mobilfunknetz sonst als "geht nicht" beim Menschen
 * ankommt. Ist es ein selbst erfasstes Rezept der anderen Person, kommt der Inhalt vom
 * Pairing-Dienst, wo ihr Geraet ihn fuer genau diesen Treffer hinterlegt hat.
 */
class FetchMatchesUseCase @Inject constructor(
    private val pairing: PairingRepository,
    private val recipeRepository: RecipeRepository,
    private val loadWebRecipe: LoadWebRecipeUseCase,
) {
    /** [onProgress] meldet, wie viele Treffer schon erledigt sind - im Guten wie im Schlechten. */
    suspend operator fun invoke(
        code: String,
        matches: List<SharedRecipe>,
        onProgress: (done: Int) -> Unit = {},
    ): List<FetchedMatch> = coroutineScope {
        val gate = Semaphore(PARALLEL)
        val done = AtomicInteger()
        matches
            .map { match ->
                async {
                    gate.withPermit { fetch(code, match) }.also { onProgress(done.incrementAndGet()) }
                }
            }.awaitAll()
    }

    private suspend fun fetch(code: String, match: SharedRecipe): FetchedMatch {
        // Wiedererkannt wird an der Adresse (auch leicht anders geschrieben) oder, ohne Adresse,
        // am Titel - nicht an der Nummer des Dienstes, die sagt darueber nichts.
        val localId = recipeRepository.findExisting(match.sourceUrl, match.title)
        if (localId != null) {
            recipeRepository.getRecipe(localId)?.let { return FetchedMatch.Ready(match, it, localId) }
        }

        val url = match.sourceUrl
        if (url != null) {
            repeat(ATTEMPTS) { attempt ->
                val geladen = loadWebRecipe(url) as? WebImportOutcome.Loaded
                if (geladen != null) return FetchedMatch.Ready(match, geladen.recipe)
                if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MS)
            }
            return FetchedMatch.NotLoaded(match)
        }

        return when (val inhalt = pairing.fetchContent(code, match.recipeId)) {
            is PairingResult.Success ->
                inhalt.value
                    ?.let { FetchedMatch.Ready(match, RecipeSharing.recipeFrom(match, it)) }
                    ?: FetchedMatch.NotYetShared(match)

            is PairingResult.Failure -> FetchedMatch.NotLoaded(match)
        }
    }

    private companion object {
        /** Mehr gleichzeitig bringt wenig und belastet die Rezeptseiten unnoetig. */
        const val PARALLEL = 4
        const val ATTEMPTS = 2
        const val RETRY_DELAY_MS = 1_200L
    }
}

/**
 * Nimmt geholte Treffer in die eigene Sammlung auf - und legt dabei nichts doppelt an.
 *
 * Was schon auf dem Geraet liegt, wird nur (wieder) zur Sammlung gezaehlt. Wiedererkannt wird
 * ein Rezept an seiner Adresse, auch wenn das andere Telefon sie leicht anders geschrieben
 * kennt ("www.", Schraegstrich am Ende) - genau daran entstanden frueher Dubletten. Ein selbst
 * erfasstes Rezept hat keine Adresse; hier zaehlt der Titel. Geprueft wird unmittelbar vor
 * dem Speichern noch einmal: Zwischen Holen und Behalten kann dasselbe Rezept auf anderem Weg
 * dazugekommen sein, etwa ueber den Wochenplan.
 *
 * Gibt zu jeder Karte (Nummer des Dienstes) die Nummer des gespeicherten Rezepts zurueck.
 */
class KeepMatchesUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val saveRecipe: SaveRecipeUseCase,
) {
    suspend operator fun invoke(ready: List<FetchedMatch.Ready>): Map<Long, Long> = buildMap {
        ready.forEach { fetched ->
            val known = fetched.localId?.takeIf { recipeRepository.getRecipe(it) != null }
                ?: recipeRepository.findExisting(fetched.recipe.sourceUrl, fetched.recipe.title)
            if (known != null) {
                // Es kann bisher nur fuer den Wochenplan gespeichert gewesen sein.
                recipeRepository.setInCollection(known, inCollection = true)
                put(fetched.match.recipeId, known)
            } else {
                val saved = saveRecipe(fetched.recipe.copy(id = 0L)) as? SaveRecipeResult.Saved
                saved?.let { put(fetched.match.recipeId, it.recipeId) }
            }
        }
    }
}

/**
 * Hinterlegt beim Dienst den Inhalt der eigenen selbst erfassten Rezepte, die gemeinsame
 * Treffer geworden sind - damit die andere Person sie uebernehmen kann.
 *
 * Nur Treffer, nur ohne Adresse, nur vom eigenen Geraet: Alles andere bleibt, wo es ist.
 * Der Dienst prueft dasselbe noch einmal.
 */
class ShareOwnMatchesUseCase @Inject constructor(
    private val pairing: PairingRepository,
    private val recipeRepository: RecipeRepository,
) {
    /** Gibt die Karten zurueck, deren Inhalt jetzt beim Dienst liegt. */
    suspend operator fun invoke(code: String, matches: List<SharedRecipe>): Set<Long> {
        val shared = mutableSetOf<Long>()
        for (match in matches) {
            if (!match.isMine || !match.isHandwritten) continue
            if (match.hasContent) {
                shared += match.recipeId
                continue
            }
            val localId = recipeRepository.findHandwrittenIdByTitle(match.title) ?: continue
            val recipe = recipeRepository.getRecipe(localId) ?: continue
            val result = pairing.shareContent(code, match.recipeId, RecipeSharing.contentOf(recipe))
            if (result is PairingResult.Success) shared += match.recipeId
        }
        return shared
    }

    /** Die Karten, bei denen es ueberhaupt etwas zu hinterlegen gibt. */
    fun wanted(matches: List<SharedRecipe>): Set<Long> =
        matches.filter { it.isMine && it.isHandwritten }.mapTo(mutableSetOf()) { it.recipeId }
}

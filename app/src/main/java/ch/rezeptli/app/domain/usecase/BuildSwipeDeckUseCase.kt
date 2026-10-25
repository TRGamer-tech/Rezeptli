package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.deck.CuratedDeckBuilder
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.profile.LanguageRegion
import ch.rezeptli.app.domain.repository.UserProfileRepository
import ch.rezeptli.app.domain.repository.WebRecipeRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Stellt den Stapel zusammen, mit dem eine Wischrunde beginnt.
 *
 * Der Vorrat kommt aus dem taeglich gebauten Verzeichnis - ein Abruf, kein Laden
 * einzelner Rezeptseiten. Die erste Karte darf auf nichts warten, was pro Rezept
 * einzeln geholt werden muesste.
 */
class BuildSwipeDeckUseCase @Inject constructor(
    private val webRepository: WebRecipeRepository,
    private val profileRepository: UserProfileRepository,
    private val builder: CuratedDeckBuilder,
) {
    /**
     * [exclude] sind Adressen, die nicht noch einmal auftauchen sollen - schon
     * gewischte Rezepte derselben Sitzung.
     */
    suspend operator fun invoke(
        size: Int = CuratedDeckBuilder.DEFAULT_SIZE,
        exclude: Set<String> = emptySet(),
        onlyRegion: LanguageRegion? = null,
    ): List<DeckEntry> {
        val fullPool = webRepository.deckPool()
        // Der Wochenplan liest Zutaten und Zubereitung selbst, um Ernaehrung und Allergien
        // einzuhalten - das geht nur in der Sprache, die er versteht.
        val pool = if (onlyRegion == null) {
            fullPool
        } else {
            fullPool.copy(
                entriesBySource = fullPool.entriesBySource.filterKeys { fullPool.origins[it]?.region == onlyRegion },
            )
        }
        if (pool.isEmpty) return emptyList()

        val profile = profileRepository.profile.first()

        return builder.build(
            entriesBySource = pool.entriesBySource,
            origins = pool.origins,
            profile = profile,
            size = size,
            exclude = exclude,
        )
    }
}

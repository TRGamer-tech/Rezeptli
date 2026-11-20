package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.di.IoDispatcher
import ch.rezeptli.app.domain.deck.CuratedDeckBuilder
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.deck.DeckTitles
import ch.rezeptli.app.domain.deck.UrlKey
import ch.rezeptli.app.domain.plan.CardRules
import ch.rezeptli.app.domain.profile.LanguageRegion
import ch.rezeptli.app.domain.repository.UserProfileRepository
import ch.rezeptli.app.domain.repository.WebRecipeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Stellt den Stapel zusammen, mit dem eine Wischrunde beginnt.
 *
 * Der Vorrat kommt aus dem taeglich gebauten Verzeichnis - ein Abruf, kein Laden
 * einzelner Rezeptseiten. Die erste Karte darf auf nichts warten, was pro Rezept
 * einzeln geholt werden muesste.
 *
 * Was sich schon am Titel entscheiden laesst, wird hier entschieden: Getraenke bei "nur
 * Essen", "Lachsfilet" fuer jemanden, der keinen Fisch mag, ein Airfryer-Rezept ohne
 * Airfryer. Das kostet keinen Abruf und haelt den Stapel schnell.
 */
class BuildSwipeDeckUseCase @Inject constructor(
    private val webRepository: WebRecipeRepository,
    private val profileRepository: UserProfileRepository,
    private val builder: CuratedDeckBuilder,
    private val titles: DeckTitles,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    /**
     * [exclude] sind Adressen, die nicht (noch einmal) auftauchen sollen - schon gewischte
     * Karten und schon gespeicherte Rezepte. [rules] sortiert aus, was sich am Titel
     * erkennen laesst; ohne Angabe gelten die Vorlieben aus dem Profil.
     */
    suspend operator fun invoke(
        size: Int = CuratedDeckBuilder.DEFAULT_SIZE,
        exclude: Set<String> = emptySet(),
        onlyRegion: LanguageRegion? = null,
        rules: CardRules? = null,
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
        val geltend = rules ?: CardRules.of(profile)

        // Ein paar tausend Titel einzuschaetzen dauert einen Moment - nicht auf dem Hauptfaden.
        return withContext(dispatcher) {
            // Verglichen wird nicht Zeichen fuer Zeichen: Ein gespeichertes Rezept kann unter
            // einer leicht anderen Adresse bekannt sein als im Verzeichnis.
            val ausgeschlossen = exclude.mapTo(HashSet()) { UrlKey.of(it) }
            val passend = pool.entriesBySource
                .mapValues { (_, eintraege) ->
                    eintraege.filter { UrlKey.of(it.url) !in ausgeschlossen && geltend.allows(titles.traits(it)) }
                }.filterValues { it.isNotEmpty() }

            builder.build(
                entriesBySource = passend,
                origins = pool.origins,
                profile = profile,
                size = size,
                cuisinesOf = { titles.traits(it).cuisines },
            )
        }
    }
}

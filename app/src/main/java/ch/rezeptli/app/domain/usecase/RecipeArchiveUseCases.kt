package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.di.IoDispatcher
import ch.rezeptli.app.domain.archive.ArchiveError
import ch.rezeptli.app.domain.archive.ArchiveException
import ch.rezeptli.app.domain.archive.ArchivedPreferences
import ch.rezeptli.app.domain.archive.DocumentAccess
import ch.rezeptli.app.domain.archive.PhotoFiles
import ch.rezeptli.app.domain.archive.RecipeArchive
import ch.rezeptli.app.domain.archive.RecipeArchiveReader
import ch.rezeptli.app.domain.archive.RecipeArchiveWriter
import ch.rezeptli.app.domain.deck.TitleKey
import ch.rezeptli.app.domain.deck.UrlKey
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.plan.AdjustPlanUseCase
import ch.rezeptli.app.domain.repository.RecipeRepository
import ch.rezeptli.app.domain.repository.UserProfileRepository
import ch.rezeptli.app.domain.settings.AppearanceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

sealed interface ExportResult {
    data class Done(
        val recipes: Int,
        val photos: Int,
        /** Stehen auch die Vorlieben in der Datei? */
        val preferences: Boolean = false,
    ) : ExportResult

    /** Die Sammlung ist leer und die Vorlieben sollten nicht mit - es gibt nichts zu sichern. */
    data object Nothing : ExportResult

    /** Die Datei liess sich nicht schreiben. */
    data object Failed : ExportResult
}

sealed interface ImportResult {
    /**
     * [skipped] gab es schon - sie wurden nicht ein zweites Mal angelegt.
     *
     * [preferences] sind die Vorlieben aus der Datei, sofern sie von den jetzigen abweichen.
     * Uebernommen sind sie damit noch nicht: Das entscheidet die Person, siehe
     * [ApplyPreferencesUseCase].
     */
    data class Done(
        val imported: Int,
        val skipped: Int,
        val preferences: ArchivedPreferences? = null,
    ) : ImportResult

    data class Failed(
        val reason: ArchiveError,
    ) : ImportResult
}

/**
 * Schreibt die ganze Sammlung in eine Datei, die die Person gewaehlt hat.
 *
 * Mit gehen die Rezepte aus "Meine Rezepte" samt eigenen Fotos und - wenn gewuenscht - die
 * Vorlieben: Wer das Telefon wechselt, soll nicht alle Fragen der Einrichtung noch einmal
 * beantworten muessen. Nicht mit gehen der Wochenplan und die Einkaufsliste - das ist der
 * Stand einer Woche auf einem Telefon, keine Sammlung.
 *
 * Die Vorlieben lassen sich weglassen: Wer die Datei jemandem gibt, will vielleicht seine
 * Rezepte teilen, aber nicht, was er nicht vertraegt.
 */
class ExportRecipesUseCase @Inject constructor(
    private val repository: RecipeRepository,
    private val photos: PhotoFiles,
    private val documents: DocumentAccess,
    private val writer: RecipeArchiveWriter,
    private val profileRepository: UserProfileRepository,
    private val appearanceRepository: AppearanceRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend operator fun invoke(
        target: String,
        includePreferences: Boolean = true,
        now: Long = System.currentTimeMillis(),
    ): ExportResult =
        withContext(io) {
            val recipes = repository
                .getFilteredRecipeIds(RecipeFilter())
                .mapNotNull { repository.getRecipe(it) }
                .sortedBy { it.title.lowercase() }
            val preferences = if (includePreferences) currentPreferences() else null
            if (recipes.isEmpty() && preferences == null) {
                // Der Dateidialog hat die Datei schon angelegt - leer soll sie nicht liegen bleiben.
                documents.discard(target)
                return@withContext ExportResult.Nothing
            }

            try {
                val out = documents.openForWriting(target) ?: return@withContext ExportResult.Failed
                val summary = writer.write(out, recipes, now, photos, preferences)
                ExportResult.Done(
                    recipes = summary.recipes,
                    photos = summary.photos,
                    preferences = preferences != null,
                )
            } catch (_: IOException) {
                // Speicher voll, Karte entfernt: Eine halbe Datei waere schlimmer als keine -
                // sie saehe aus wie eine Sicherung und waere keine.
                documents.discard(target)
                ExportResult.Failed
            }
        }

    private suspend fun currentPreferences() = ArchivedPreferences(
        profile = profileRepository.profile.first(),
        theme = appearanceRepository.theme.first(),
        reduceMotion = appearanceRepository.reduceMotion.first(),
    )
}

/**
 * Liest Rezepte aus einer Exportdatei in die Sammlung.
 *
 * Es kommt nur dazu, was noch fehlt: Ein Rezept mit derselben Quelle, oder ein selbst
 * erfasstes mit demselben Titel und denselben Zutaten, gibt es schon und wird uebergangen.
 * So laesst sich dieselbe Datei gefahrlos zweimal einlesen - und bestehende Rezepte, die
 * seither bearbeitet wurden, werden nicht ueberschrieben.
 *
 * Stehen in der Datei auch Vorlieben, werden sie hier nur gemeldet, nicht uebernommen. Die
 * Datei kann von jemand anderem stammen - und wer keine Nuesse vertraegt, soll diese Angabe
 * nicht verlieren, weil er ein paar Rezepte einliest.
 */
class ImportRecipesUseCase @Inject constructor(
    private val repository: RecipeRepository,
    private val photos: PhotoFiles,
    private val documents: DocumentAccess,
    private val reader: RecipeArchiveReader,
    private val profileRepository: UserProfileRepository,
    private val appearanceRepository: AppearanceRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend operator fun invoke(source: String): ImportResult = withContext(io) {
        val archive = try {
            val input = documents.openForReading(source)
                ?: return@withContext ImportResult.Failed(ArchiveError.UNREADABLE)
            input.use { reader.read(it, photos) }
        } catch (e: ArchiveException) {
            return@withContext ImportResult.Failed(e.error)
        } catch (_: IOException) {
            return@withContext ImportResult.Failed(ArchiveError.UNREADABLE)
        }
        val recipes = archive.recipes
        val preferences = archive.preferences?.takeIf { differsFromCurrent(it) }
        // Eine Datei nur mit Vorlieben ist in Ordnung - leer ist sie erst ohne beides.
        if (recipes.isEmpty() && archive.preferences == null) {
            return@withContext ImportResult.Failed(ArchiveError.EMPTY)
        }

        val own = repository
            .getFilteredRecipeIds(RecipeFilter())
            .mapNotNull { repository.getRecipe(it) }
        val knownAddresses = own.mapNotNullTo(mutableSetOf()) { it.sourceUrl?.let(UrlKey::of) }
        val knownDishes = own.filter { it.sourceUrl == null }.mapTo(mutableSetOf(), ::fingerprint)

        var imported = 0
        var skipped = 0
        recipes.forEach { recipe ->
            val address = recipe.sourceUrl
            val isKnown = if (address != null) {
                UrlKey.of(address) in knownAddresses
            } else {
                fingerprint(recipe) in knownDishes
            }
            // Stand das Rezept bisher nur im Wochenplan, kommt es in die Sammlung, statt doppelt zu entstehen.
            val planOnly = if (isKnown || address == null) null else repository.findIdBySourceUrl(address)

            when {
                isKnown -> skipped += 1

                planOnly != null -> {
                    repository.setInCollection(planOnly, inCollection = true)
                    imported += 1
                }

                else -> {
                    repository.saveRecipe(recipe.copy(id = 0L, inCollection = true))
                    imported += 1
                }
            }

            if (address != null) knownAddresses += UrlKey.of(address) else knownDishes += fingerprint(recipe)
            // Das Foto eines uebergangenen Rezepts wurde beim Lesen schon abgelegt - wieder weg damit.
            if (isKnown ||
                planOnly != null
            ) {
                recipe.photoUri?.takeUnless(RecipeArchive::isWebAddress)?.let(photos::delete)
            }
        }
        ImportResult.Done(imported = imported, skipped = skipped, preferences = preferences)
    }

    /** Gleichen die Vorlieben der Datei den jetzigen, gibt es nichts zu fragen. */
    private suspend fun differsFromCurrent(offered: ArchivedPreferences): Boolean {
        val current = profileRepository.profile.first()
        val sameProfile = offered.profile.copy(onboardingCompleted = current.onboardingCompleted) == current
        val sameTheme = offered.theme == null || offered.theme == appearanceRepository.theme.first()
        val sameMotion =
            offered.reduceMotion == null || offered.reduceMotion == appearanceRepository.reduceMotion.first()
        return !(sameProfile && sameTheme && sameMotion)
    }

    /** Woran ein selbst erfasstes Rezept wiedererkannt wird: Titel und Zutaten, ohne Gross und Klein. */
    private fun fingerprint(recipe: Recipe): String {
        val title = TitleKey.of(recipe.title)
        val ingredients = recipe.ingredients.map { it.name.trim().lowercase() }.sorted()
        return (listOf(title) + ingredients).joinToString("\u0000")
    }
}

/**
 * Uebernimmt die Vorlieben aus einer Exportdatei - erst, wenn die Person es ausdruecklich will.
 *
 * Sie ersetzen die jetzigen ganz: Ein halb uebernommenes Profil (ihre Unvertraeglichkeiten,
 * meine Abneigungen) waere weder das eine noch das andere. Ob die Einrichtung schon gelaufen
 * ist, bleibt eine Sache dieses Telefons. Danach folgt der bestehende Wochenplan den neuen
 * Angaben, so wie nach einer Aenderung auf der Vorlieben-Seite: Mengen und Preise passen
 * sich an, die Gerichte bleiben.
 */
class ApplyPreferencesUseCase @Inject constructor(
    private val profileRepository: UserProfileRepository,
    private val appearanceRepository: AppearanceRepository,
    private val adjustPlan: AdjustPlanUseCase,
) {
    suspend operator fun invoke(preferences: ArchivedPreferences) {
        profileRepository.update { current ->
            preferences.profile.copy(onboardingCompleted = current.onboardingCompleted)
        }
        preferences.theme?.let { appearanceRepository.setTheme(it) }
        preferences.reduceMotion?.let { appearanceRepository.setReduceMotion(it) }
        adjustPlan()
    }
}

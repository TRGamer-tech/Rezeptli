package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.di.IoDispatcher
import ch.rezeptli.app.domain.archive.ArchiveError
import ch.rezeptli.app.domain.archive.ArchiveException
import ch.rezeptli.app.domain.archive.DocumentAccess
import ch.rezeptli.app.domain.archive.PhotoFiles
import ch.rezeptli.app.domain.archive.RecipeArchive
import ch.rezeptli.app.domain.archive.RecipeArchiveReader
import ch.rezeptli.app.domain.archive.RecipeArchiveWriter
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.repository.RecipeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

sealed interface ExportResult {
    data class Done(
        val recipes: Int,
        val photos: Int,
    ) : ExportResult

    /** Die Sammlung ist leer - es gibt nichts zu sichern. */
    data object Nothing : ExportResult

    /** Die Datei liess sich nicht schreiben. */
    data object Failed : ExportResult
}

sealed interface ImportResult {
    /** [skipped] gab es schon - sie wurden nicht ein zweites Mal angelegt. */
    data class Done(
        val imported: Int,
        val skipped: Int,
    ) : ImportResult

    data class Failed(
        val reason: ArchiveError,
    ) : ImportResult
}

/**
 * Schreibt die ganze Sammlung in eine Datei, die die Person gewaehlt hat.
 *
 * Mit gehen die Rezepte aus "Meine Rezepte" samt eigenen Fotos. Nicht mit gehen der
 * Wochenplan, die Einkaufsliste und die Vorlieben - das ist der Stand einer Woche auf einem
 * Telefon, keine Sammlung.
 */
class ExportRecipesUseCase @Inject constructor(
    private val repository: RecipeRepository,
    private val photos: PhotoFiles,
    private val documents: DocumentAccess,
    private val writer: RecipeArchiveWriter,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend operator fun invoke(target: String, now: Long = System.currentTimeMillis()): ExportResult =
        withContext(io) {
            val recipes = repository
                .getFilteredRecipeIds(RecipeFilter())
                .mapNotNull { repository.getRecipe(it) }
                .sortedBy { it.title.lowercase() }
            if (recipes.isEmpty()) {
                // Der Dateidialog hat die Datei schon angelegt - leer soll sie nicht liegen bleiben.
                documents.discard(target)
                return@withContext ExportResult.Nothing
            }

            try {
                val out = documents.openForWriting(target) ?: return@withContext ExportResult.Failed
                val summary = writer.write(out, recipes, now, photos)
                ExportResult.Done(recipes = summary.recipes, photos = summary.photos)
            } catch (_: IOException) {
                // Speicher voll, Karte entfernt: Eine halbe Datei waere schlimmer als keine -
                // sie saehe aus wie eine Sicherung und waere keine.
                documents.discard(target)
                ExportResult.Failed
            }
        }
}

/**
 * Liest Rezepte aus einer Exportdatei in die Sammlung.
 *
 * Es kommt nur dazu, was noch fehlt: Ein Rezept mit derselben Quelle, oder ein selbst
 * erfasstes mit demselben Titel und denselben Zutaten, gibt es schon und wird uebergangen.
 * So laesst sich dieselbe Datei gefahrlos zweimal einlesen - und bestehende Rezepte, die
 * seither bearbeitet wurden, werden nicht ueberschrieben.
 */
class ImportRecipesUseCase @Inject constructor(
    private val repository: RecipeRepository,
    private val photos: PhotoFiles,
    private val documents: DocumentAccess,
    private val reader: RecipeArchiveReader,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend operator fun invoke(source: String): ImportResult = withContext(io) {
        val recipes = try {
            val input = documents.openForReading(source)
                ?: return@withContext ImportResult.Failed(ArchiveError.UNREADABLE)
            input.use { reader.read(it, photos) }
        } catch (e: ArchiveException) {
            return@withContext ImportResult.Failed(e.error)
        } catch (_: IOException) {
            return@withContext ImportResult.Failed(ArchiveError.UNREADABLE)
        }
        if (recipes.isEmpty()) return@withContext ImportResult.Failed(ArchiveError.EMPTY)

        val own = repository
            .getFilteredRecipeIds(RecipeFilter())
            .mapNotNull { repository.getRecipe(it) }
        val knownAddresses = own.mapNotNullTo(mutableSetOf()) { it.sourceUrl }
        val knownDishes = own.filter { it.sourceUrl == null }.mapTo(mutableSetOf(), ::fingerprint)

        var imported = 0
        var skipped = 0
        recipes.forEach { recipe ->
            val address = recipe.sourceUrl
            val isKnown = if (address != null) address in knownAddresses else fingerprint(recipe) in knownDishes
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

            if (address != null) knownAddresses += address else knownDishes += fingerprint(recipe)
            // Das Foto eines uebergangenen Rezepts wurde beim Lesen schon abgelegt - wieder weg damit.
            if (isKnown ||
                planOnly != null
            ) {
                recipe.photoUri?.takeUnless(RecipeArchive::isWebAddress)?.let(photos::delete)
            }
        }
        ImportResult.Done(imported = imported, skipped = skipped)
    }

    /** Woran ein selbst erfasstes Rezept wiedererkannt wird: Titel und Zutaten, ohne Gross und Klein. */
    private fun fingerprint(recipe: Recipe): String {
        val title = recipe.title.trim().lowercase()
        val ingredients = recipe.ingredients.map { it.name.trim().lowercase() }.sorted()
        return (listOf(title) + ingredients).joinToString("\u0000")
    }
}

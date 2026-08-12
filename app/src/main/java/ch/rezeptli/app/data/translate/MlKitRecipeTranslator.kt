package ch.rezeptli.app.data.translate

import ch.rezeptli.app.di.IoDispatcher
import ch.rezeptli.app.domain.translate.RecipeTranslator
import ch.rezeptli.app.domain.translate.SourceLanguage
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uebersetzt auf dem Geraet, mit den Sprachmodellen von ML Kit.
 *
 * Auf dem Geraet und nicht in der Cloud: Rezepttexte verlassen das Telefon damit
 * nicht, was zur uebrigen Haltung der App passt - und es kostet nichts pro Zeichen.
 * Der Preis ist ein Modell von rund 30 MB je Sprachpaar. Es wird beim ersten Bedarf
 * geholt, nur ueber WLAN, und danach behalten.
 *
 * Schlaegt etwas fehl - kein WLAN, Modell noch nicht da, Abbruch -, gibt es `null`
 * zurueck statt zu werfen: Der Import laeuft dann mit dem Original weiter.
 */
@Singleton
class MlKitRecipeTranslator @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RecipeTranslator {
    private val mutex = Mutex()
    private val uebersetzer = mutableMapOf<SourceLanguage, Translator>()

    override suspend fun toGerman(texts: List<String>, from: SourceLanguage): List<String>? {
        if (texts.isEmpty()) return emptyList()

        return withContext(ioDispatcher) {
            val werkzeug = runCatching { hole(from) }.getOrNull() ?: return@withContext null

            runCatching {
                // Zeile fuer Zeile: ML Kit uebersetzt Saetze, keine Dokumente. Ein
                // zusammengefuegter Block kaeme in anderer Gliederung zurueck, und die
                // Zuordnung zu Zutaten und Schritten waere dahin.
                texts.map { text ->
                    if (text.isBlank()) text else werkzeug.translate(text).await()
                }
            }.getOrNull()
        }
    }

    private suspend fun hole(from: SourceLanguage): Translator {
        val werkzeug = mutex.withLock {
            uebersetzer.getOrPut(from) {
                val options = TranslatorOptions
                    .Builder()
                    .setSourceLanguage(from.toMlKit())
                    .setTargetLanguage(TranslateLanguage.GERMAN)
                    .build()
                Translation.getClient(options)
            }
        }

        // Ohne WLAN wird nichts geladen - 30 MB ueber Mobilfunk waeren eine Zumutung,
        // und niemand hat danach gefragt.
        val bedingungen = DownloadConditions.Builder().requireWifi().build()
        werkzeug.downloadModelIfNeeded(bedingungen).await()
        return werkzeug
    }

    private fun SourceLanguage.toMlKit(): String = when (this) {
        SourceLanguage.FRANZOESISCH -> TranslateLanguage.FRENCH
        SourceLanguage.ITALIENISCH -> TranslateLanguage.ITALIAN
    }
}

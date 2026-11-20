package ch.rezeptli.app.domain.archive

import ch.rezeptli.app.domain.model.Recipe
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/** Was beim Schreiben einer Exportdatei herauskam. */
data class ArchiveSummary(
    val recipes: Int,
    val photos: Int,
)

/** Schreibt die Sammlung als eine Datei: ein ZIP mit den Fotos und `rezepte.json`. */
class RecipeArchiveWriter @Inject constructor() {
    /**
     * Schreibt [recipes] nach [out] und schliesst [out].
     *
     * Eigene Fotos wandern als Dateien ins Archiv. Fotos, die im Netz liegen (bei Rezepten aus
     * dem Web), bleiben eine Adresse - sie mitzunehmen hiesse, fremde Bilder zu kopieren.
     * Laesst sich ein Foto nicht lesen, geht das Rezept ohne Foto mit, statt den Export zu kippen.
     */
    fun write(out: OutputStream, recipes: List<Recipe>, exportedAt: Long, photos: PhotoFiles): ArchiveSummary {
        var photoCount = 0
        ZipOutputStream(out.buffered()).use { zip ->
            val archived = recipes.mapIndexed { index, recipe ->
                val photo = recipe.photoUri?.takeIf { it.isNotBlank() }
                val reference = when {
                    photo == null -> null
                    RecipeArchive.isWebAddress(photo) -> photo
                    else -> photos.open(photo)?.use { input ->
                        val name = "${RecipeArchive.PHOTO_FOLDER}${index + 1}.jpg"
                        zip.putNextEntry(ZipEntry(name))
                        input.copyTo(zip)
                        zip.closeEntry()
                        photoCount += 1
                        name
                    }
                }
                ArchivedRecipe(recipe, reference)
            }
            // Der Text kommt zuletzt: Erst jetzt steht fest, welche Fotos wirklich dabei sind.
            zip.putNextEntry(ZipEntry(RecipeArchive.JSON_ENTRY))
            zip.write(RecipeArchive.encode(archived, exportedAt).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return ArchiveSummary(recipes = recipes.size, photos = photoCount)
    }
}

/**
 * Liest eine Exportdatei.
 *
 * Die Datei kann von irgendwoher kommen. Deshalb gilt: Namen aus dem Archiv werden nie als
 * Pfad benutzt (Fotos bekommen beim Ablegen einen neuen Namen), alles hat eine Obergrenze,
 * und als Foto zaehlt nur, was wie ein Bild anfaengt.
 */
class RecipeArchiveReader @Inject constructor() {
    /**
     * Liefert die Rezepte aus [input] - ihre Fotos sind dann schon ueber [photos] abgelegt.
     *
     * Wirft [ArchiveException], wenn die Datei keine Exportdatei ist. Was bis dahin an Fotos
     * abgelegt wurde, ist dann wieder weggeraeumt.
     */
    fun read(input: InputStream, photos: PhotoFiles): List<Recipe> {
        val buffered = BufferedInputStream(input)
        buffered.mark(ZIP_MAGIC.size)
        val start = ByteArray(ZIP_MAGIC.size)
        val count = buffered.readFully(start)
        buffered.reset()

        // Auch die blosse Textdatei wird angenommen: Wer das Archiv ausgepackt hat, soll die
        // Rezepte trotzdem einlesen koennen - dann eben ohne die eigenen Fotos.
        if (count < ZIP_MAGIC.size || !start.contentEquals(ZIP_MAGIC)) {
            return RecipeArchive.decode(buffered.readText(MAX_JSON_BYTES)).map { it.withPhoto(emptyMap()) }
        }

        val saved = mutableMapOf<String, String>()
        try {
            var text: String? = null
            var photoBytes = 0L
            var otherBytes = 0L
            ZipInputStream(buffered).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    val counting = CountingInputStream(zip)
                    when {
                        entry.isDirectory -> Unit

                        name == RecipeArchive.JSON_ENTRY -> text = counting.readText(MAX_JSON_BYTES)

                        name.startsWith(RecipeArchive.PHOTO_FOLDER) && name !in saved -> {
                            if (saved.size >= MAX_PHOTOS) throw ArchiveException(ArchiveError.TOO_LARGE)
                            savePhoto(counting, photos)?.let { saved[name] = it }
                            // Ein "Foto" jenseits jeder Kameragroesse ist keines - hier wird abgebrochen
                            // statt weitergelesen: Ein Archiv kann beim Auspacken beliebig gross werden.
                            if (counting.count >= MAX_PHOTO_BYTES) throw ArchiveException(ArchiveError.TOO_LARGE)
                            photoBytes += counting.count
                            if (photoBytes > MAX_PHOTO_BYTES_TOTAL) throw ArchiveException(ArchiveError.TOO_LARGE)
                        }

                        // Alles andere gehoert nicht dazu und wird ueberlesen - aber nicht unbegrenzt.
                        else -> {
                            otherBytes += counting.skipAll(MAX_OTHER_BYTES - otherBytes)
                            if (otherBytes >= MAX_OTHER_BYTES) throw ArchiveException(ArchiveError.TOO_LARGE)
                        }
                    }
                    zip.closeEntry()
                }
            }

            val recipes = RecipeArchive
                .decode(text ?: throw ArchiveException(ArchiveError.NOT_AN_ARCHIVE))
                .map { it.withPhoto(saved) }
            // Fotos, auf die kein Rezept zeigt, haben auf dem Telefon nichts verloren.
            val used = recipes.mapNotNullTo(mutableSetOf()) { it.photoUri }
            saved.values.filterNot { it in used }.forEach(photos::delete)
            return recipes
        } catch (e: ArchiveException) {
            saved.values.forEach(photos::delete)
            throw e
        } catch (_: IOException) {
            // Abgeschnitten oder beschaedigt: Ein halbes Archiv ist keines.
            saved.values.forEach(photos::delete)
            throw ArchiveException(ArchiveError.UNREADABLE)
        } catch (_: IllegalArgumentException) {
            // So meldet Java einen Eintrag, dessen Name kein gueltiger Text ist.
            saved.values.forEach(photos::delete)
            throw ArchiveException(ArchiveError.UNREADABLE)
        }
    }

    private fun ArchivedRecipe.withPhoto(saved: Map<String, String>): Recipe =
        recipe.copy(photoUri = photo?.let { if (RecipeArchive.isWebAddress(it)) it else saved[it] })

    /** Legt ein Foto ab, wenn es wie ein Bild anfaengt - sonst wird der Eintrag uebergangen. */
    private fun savePhoto(input: InputStream, photos: PhotoFiles): String? {
        val header = ByteArray(HEADER_SIZE)
        val count = input.readFully(header)
        if (!looksLikeImage(header, count)) return null
        return photos.save(SequenceInputStream(ByteArrayInputStream(header, 0, count), input), MAX_PHOTO_BYTES)
    }

    /** Zaehlt mit, wie viel gelesen wurde - ohne den Strom dahinter zu schliessen. */
    private class CountingInputStream(
        private val source: InputStream,
    ) : InputStream() {
        var count = 0L
            private set

        override fun read(): Int = source.read().also { if (it >= 0) count += 1 }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            source.read(buffer, offset, length).also { if (it > 0) count += it }

        // Der Eintrag ist zu Ende gelesen - das Archiv dahinter bleibt offen.
        override fun close() = Unit
    }

    companion object {
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        private const val HEADER_SIZE = 12

        /** Der Text einer sehr grossen Sammlung ist ein paar Megabyte gross - nicht mehr. */
        private const val MAX_JSON_BYTES = 64L * 1024 * 1024
        private const val MAX_PHOTOS = 20_000

        /** Ein Foto vom Telefon ist ein paar Megabyte gross. */
        const val MAX_PHOTO_BYTES = 25L * 1024 * 1024
        private const val MAX_PHOTO_BYTES_TOTAL = 2L * 1024 * 1024 * 1024
        private const val MAX_OTHER_BYTES = 64L * 1024 * 1024

        /** Liest, bis [target] voll oder der Strom zu Ende ist, und liefert die Anzahl Bytes. */
        private fun InputStream.readFully(target: ByteArray): Int {
            var filled = 0
            while (filled < target.size) {
                val read = read(target, filled, target.size - filled)
                if (read < 0) break
                filled += read
            }
            return filled
        }

        /** Liest den Strom als Text - und bricht ab, wenn er groesser ist, als eine Sammlung sein kann. */
        private fun InputStream.readText(maxBytes: Long): String {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > maxBytes) throw ArchiveException(ArchiveError.TOO_LARGE)
            }
            return out.toString(Charsets.UTF_8.name())
        }

        /** Liest hoechstens [limit] Bytes weg und liefert, wie viele es waren. */
        private fun InputStream.skipAll(limit: Long): Long {
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0L
            while (total < limit) {
                val read = read(buffer)
                if (read < 0) break
                total += read
            }
            return total
        }

        private const val BUFFER_SIZE = 16 * 1024

        /**
         * Ob die ersten Bytes zu einem Bild gehoeren: JPEG, PNG, GIF, WebP oder HEIF/AVIF.
         * Mehr legt der Fotowaehler des Telefons nicht ab.
         */
        fun looksLikeImage(header: ByteArray, length: Int = header.size): Boolean {
            fun at(index: Int): Int = if (index < length) header[index].toInt() and 0xFF else -1

            fun ascii(from: Int, text: String): Boolean = text.indices.all { at(from + it) == text[it].code }

            return when {
                at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> true
                at(0) == 0x89 && ascii(1, "PNG") -> true
                ascii(0, "GIF8") -> true
                ascii(0, "RIFF") && ascii(8, "WEBP") -> true
                ascii(4, "ftyp") -> true
                else -> false
            }
        }
    }
}

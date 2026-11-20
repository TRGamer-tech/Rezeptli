package ch.rezeptli.app.audit

import ch.rezeptli.app.data.web.PageFetcher
import ch.rezeptli.app.data.web.PrebuiltIndexClient
import ch.rezeptli.app.data.web.RecipeSource
import ch.rezeptli.app.data.web.RecipeSourceCatalog
import ch.rezeptli.app.data.web.RecipeWebClient
import ch.rezeptli.app.data.web.RobotsParser
import ch.rezeptli.app.data.web.SampleEntry
import ch.rezeptli.app.data.web.StructuredRecipeExtractor
import ch.rezeptli.app.domain.deck.RecipeTitles
import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientNameNormalizer
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.parser.IngredientTextParser
import ch.rezeptli.app.domain.plan.DietClass
import ch.rezeptli.app.domain.plan.RecipeClassifier
import ch.rezeptli.app.domain.plan.RecipeTraits
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.LanguageRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Prueft die Einstufung der Rezepte an den echten Rezeptseiten.
 *
 * Ob "keine Pilze" haelt, entscheidet sich nicht an ausgedachten Beispielen, sondern an dem,
 * was die Quellen wirklich schreiben: "Egerlinge", "Schwammerl", "braune Champignons, in
 * Scheiben". Dieser Lauf zieht Karten aus dem Verzeichnis, liest ihre Seiten mit denselben
 * Bausteinen wie die App (samt robots.txt und Wartezeiten) und schreibt auf, woran die
 * Einstufung haengt - und wo ein Verdacht besteht, dass sie etwas uebersieht.
 *
 * Er laeuft nur auf Wunsch (`REZEPTLI_AUDIT=1`, in der CI die Aufgabe "fehler-pruefen"):
 * Ein Test, der vom Netz und vom Tagesangebot fremder Seiten abhaengt, hat in der normalen
 * Pruefung nichts verloren. Mit `REZEPTLI_AUDIT=probe` laeuft er ohne Netz ueber ein paar
 * eingebaute Seiten - damit sich der Bericht selbst pruefen laesst.
 */
class FilterAuditTest {
    private val classifier = RecipeClassifier()
    private val parser = IngredientTextParser()
    private val extractor = StructuredRecipeExtractor()
    private val out = StringBuilder()

    @Test
    fun einstufungAnEchtenSeiten() {
        val modus = System.getenv("REZEPTLI_AUDIT").orEmpty()
        assumeTrue(modus == "1" || modus == "probe", "laeuft nur mit REZEPTLI_AUDIT=1")

        runBlocking {
            if (modus == "probe") {
                run(PROBE_ENTRIES, PROBE_SOURCES, pagesPerSource = 4) { url, _ -> PROBE_PAGES[url] }
            } else {
                val client = OkHttpClient
                    .Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
                val entries = PrebuiltIndexClient(client, Dispatchers.IO).deckSample()
                assertTrue(entries != null, "Das Verzeichnis war nicht zu erreichen")
                val web: PageFetcher = RecipeWebClient(client, RobotsParser(), Dispatchers.IO)
                val deutsch = RecipeSourceCatalog.SEARCHABLE.filter { it.country.region == LanguageRegion.DEUTSCH }
                run(entries.orEmpty(), deutsch, pagesPerSource = pagesPerSource()) { url, source ->
                    runCatching { web.fetch(url, source) }.getOrNull()
                }
            }
        }

        val datei = File(System.getProperty("user.dir"), "build/audit/filter-audit.txt")
        datei.parentFile.mkdirs()
        datei.writeText(out.toString())
        println(out)
    }

    private fun pagesPerSource(): Int = System.getenv("REZEPTLI_AUDIT_SEITEN")?.toIntOrNull() ?: DEFAULT_PAGES

    private suspend fun run(
        entries: List<SampleEntry>,
        sources: List<RecipeSource>,
        pagesPerSource: Int,
        fetch: suspend (String, RecipeSource) -> String?,
    ) {
        val byId = sources.associateBy { it.id }
        val german = entries.filter { it.sourceId in byId }
        line("Verzeichnis: ${entries.size} Karten, davon ${german.size} aus deutschsprachigen Quellen")
        german.groupBy { it.sourceId }.forEach { (id, liste) -> line("  $id: ${liste.size}") }

        titles(german.map { RecipeTitles.clean(it.title, it.url, byId.getValue(it.sourceId).name) })
        pages(german, byId, pagesPerSource, fetch)
    }

    // ------------------------------------------------------------------ Titel

    /** Alle Titel des Verzeichnisses: Woran haengt jede Abneigung, und was sieht verdaechtig aus? */
    private fun titles(titles: List<String>) {
        section("TITEL (${titles.size})")
        val trigger = Tally()
        val verdacht = Tally()
        val fleisch = Tally()

        titles.forEach { title ->
            val traits = classifier.classifyTitle(title)
            val words = wordsOf(title)
            words.forEach { word ->
                asTitle(word).dislikes.forEach { trigger.add(it.name, word, title) }
                when (asTitle(word).diet) {
                    DietClass.FLEISCH -> fleisch.add("FLEISCH", word, title)
                    DietClass.FISCH -> fleisch.add("FISCH", word, title)
                    else -> Unit
                }
            }
            val text = IngredientNameNormalizer.normalize(title)
            SUSPECTS.forEach { (dislike, regex) ->
                if (dislike in traits.dislikes) return@forEach
                regex.findAll(text).forEach { verdacht.add(dislike.name, wordAround(text, it.range), title) }
            }
        }

        line("\n-- Woran die Abneigungen im Titel haengen (Wort: Anzahl, Beispiel)")
        trigger.print(TOP_TRIGGERS)
        line("\n-- Woran Fleisch und Fisch im Titel haengen")
        fleisch.print(TOP_TRIGGERS)
        line("\n-- Verdacht im Titel, aber nicht erkannt")
        verdacht.print(TOP_SUSPECTS)
    }

    // ------------------------------------------------------------------ Seiten

    private class Page(
        val entry: SampleEntry,
        val title: String,
        val lines: List<String>,
        val instructions: String,
        val keywords: List<String>,
        val recipe: Recipe,
        val traits: RecipeTraits,
    )

    private suspend fun pages(
        entries: List<SampleEntry>,
        sources: Map<String, RecipeSource>,
        perSource: Int,
        fetch: suspend (String, RecipeSource) -> String?,
    ) {
        section("SEITEN")
        val random = Random(SEED)
        val pilzTitel = SUSPECTS.getValue(Dislike.PILZE)
        val stats = mutableMapOf<String, IntArray>()

        val loaded = coroutineScope {
            entries
                .groupBy { it.sourceId }
                .map { (id, liste) ->
                    val source = sources.getValue(id)
                    val gemischt = liste.shuffled(random)
                    // Ein paar Karten, deren Titel schon Pilze nennt: An ihnen zeigt sich, wie die
                    // Quelle Pilze in der Zutatenliste schreibt.
                    val gezielt = gemischt
                        .filter { pilzTitel.containsMatchIn(IngredientNameNormalizer.normalize(it.title)) }
                        .take(TARGETED)
                    val auswahl = (gemischt.take(perSource) + gezielt).distinctBy { it.url }
                    val zahlen = IntArray(STAT_FIELDS).also { stats[id] = it }

                    async(Dispatchers.IO) {
                        val seiten = mutableListOf<Page>()
                        withTimeoutOrNull(SOURCE_BUDGET_MS) {
                            auswahl.forEach { entry ->
                                zahlen[TRIED] += 1
                                val html = withTimeoutOrNull(PAGE_TIMEOUT_MS) { fetch(entry.url, source) }
                                if (html == null) {
                                    zahlen[FAILED] += 1
                                    return@forEach
                                }
                                val page = read(entry, source, html)
                                when {
                                    page == null -> zahlen[NO_RECIPE] += 1
                                    page.recipe.ingredients.isEmpty() -> zahlen[NO_INGREDIENTS] += 1
                                    else -> {
                                        zahlen[READ] += 1
                                        seiten += page
                                    }
                                }
                            }
                        }
                        seiten
                    }
                }.awaitAll()
                .flatten()
        }

        line("Quelle: versucht / gelesen / nicht geladen / kein Rezept / keine Zutaten")
        stats.forEach { (id, z) ->
            line("  $id: ${z[TRIED]} / ${z[READ]} / ${z[FAILED]} / ${z[NO_RECIPE]} / ${z[NO_INGREDIENTS]}")
        }
        line("Gelesen: ${loaded.size} Seiten")
        Dislike.entries.forEach { dislike ->
            line("  ${dislike.name}: ${loaded.count { dislike in it.traits.dislikes }}")
        }
        DietClass.entries.forEach { diet -> line("  Ernaehrung $diet: ${loaded.count { it.traits.diet == diet }}") }

        val trigger = Tally()
        val diet = Tally()
        val allergen = Tally()
        val missed = Tally()
        val neutral = mutableMapOf<String, Int>()
        val titleOnly = mutableListOf<String>()

        loaded.forEach { page ->
            val nameWords = page.recipe.ingredients
                .flatMap { wordsOf(it.canonicalName ?: it.name) + wordsOf(it.name) }
                .toSet()
            nameWords.forEach { word ->
                val wort = asIngredient(word)
                wort.dislikes.forEach { trigger.add(it.name, word, page.title) }
                wort.allergens.forEach { allergen.add(it.name, word, page.title) }
                when (wort.diet) {
                    DietClass.FLEISCH -> diet.add("FLEISCH", word, page.title)
                    DietClass.FISCH -> diet.add("FISCH", word, page.title)
                    DietClass.VEGETARISCH -> diet.add("VEGETARISCH", word, page.title)
                    DietClass.VEGAN -> if (wort.allergens.isEmpty() && wort.dislikes.isEmpty()) {
                        neutral.merge(word, 1, Int::plus)
                    }
                }
            }

            val names = IngredientNameNormalizer.normalize(page.recipe.ingredients.joinToString(" ") { it.name })
            val wholeLines = IngredientNameNormalizer.normalize(page.lines.joinToString(" "))
            val steps = IngredientNameNormalizer.normalize(page.instructions)
            val tags = IngredientNameNormalizer.normalize(page.keywords.joinToString(" "))
            SUSPECTS.forEach { (dislike, regex) ->
                if (dislike in page.traits.dislikes) return@forEach
                val (wo, text) = listOf("ZUTAT" to names, "NOTIZ" to wholeLines, "TEXT" to steps, "TAG" to tags)
                    .firstOrNull { regex.containsMatchIn(it.second) }
                    ?: return@forEach
                regex.findAll(text).map { wordAround(text, it.range) }.distinct().forEach { word ->
                    missed.add("${dislike.name} $wo", word, page.title)
                }
            }

            // Der Titel nennt etwas, die Zutaten aber nicht: Dort steht es unter einem Namen,
            // den die Einstufung nicht kennt.
            val ausTitel = classifier.classifyTitle(page.title).dislikes
            val ausZutaten = nameWords.flatMapTo(mutableSetOf()) { asIngredient(it).dislikes }
            (ausTitel - ausZutaten - setOf(Dislike.VEGAN, Dislike.SCHARF)).forEach { dislike ->
                if (titleOnly.size < TITLE_ONLY_LIMIT) {
                    titleOnly += "${dislike.name}: ${page.title} <- ${page.lines.joinToString(" | ").take(LINE_LIMIT)}"
                }
            }
        }

        line("\n-- Woran die Abneigungen in den Zutaten haengen")
        trigger.print(TOP_TRIGGERS)
        line("\n-- Woran die Ernaehrung haengt")
        diet.print(TOP_TRIGGERS)
        line("\n-- Woran die Unvertraeglichkeiten haengen")
        allergen.print(TOP_TRIGGERS)
        line("\n-- Verdacht, aber nicht erkannt (wo gefunden)")
        missed.print(TOP_SUSPECTS)
        line("\n-- Im Titel erkannt, in den Zutaten nicht")
        titleOnly.forEach { line("  $it") }
        line("\n-- Zutatenwoerter, die nichts ausloesen (Wort Seiten)")
        line(
            neutral.entries
                .sortedByDescending { it.value }
                .take(TOP_NEUTRAL)
                .joinToString(", ") { "${it.key} ${it.value}" },
        )
    }

    /** Liest eine Seite so, wie die App sie fuer eine Wischkarte liest. */
    private fun read(entry: SampleEntry, source: RecipeSource, html: String): Page? {
        val web = extractor.extract(html, entry.url, source.name) ?: return null
        val recipe = Recipe(
            title = web.title,
            instructions = (web.instructionSteps.ifEmpty { listOf(web.instructions) }).joinToString("\n"),
            ingredients = web.ingredientLines
                .mapNotNull { parser.parseLine(it) }
                .mapIndexed { index, parsed -> parsed.toIngredient(index) },
            tags = web.keywords,
            prepTimeMinutes = web.totalMinutes,
        )
        return Page(
            entry = entry,
            title = web.title,
            lines = web.ingredientLines,
            instructions = recipe.instructions,
            keywords = web.keywords,
            recipe = recipe,
            traits = classifier.classify(recipe),
        )
    }

    // ------------------------------------------------------------------ Hilfen

    private val alsZutat = HashMap<String, RecipeTraits>()
    private val alsTitel = HashMap<String, RecipeTraits>()

    /** Was ein einzelnes Wort ausloest, wenn es als Zutat dasteht. */
    private fun asIngredient(word: String): RecipeTraits = alsZutat.getOrPut(word) {
        classifier.classify(Recipe(title = "", ingredients = listOf(Ingredient(name = word))))
    }

    /** Was ein einzelnes Wort ausloest, wenn es im Titel steht. */
    private fun asTitle(word: String): RecipeTraits = alsTitel.getOrPut(word) { classifier.classifyTitle(word) }

    private fun wordsOf(text: String): List<String> =
        IngredientNameNormalizer.normalize(text).split(' ').filter { it.length >= 2 }

    /** Das ganze Wort um eine Fundstelle - "garnieren" statt "niere". */
    private fun wordAround(text: String, range: IntRange): String {
        val start = text.lastIndexOf(' ', range.first).let { if (it < 0) 0 else it + 1 }
        val end = text.indexOf(' ', range.last).let { if (it < 0) text.length else it }
        return text.substring(start, end)
    }

    private fun section(title: String) = line("\n==================== $title")

    private fun line(text: String) {
        out.append(text).append('\n')
    }

    /** Zaehlt je Gruppe, welches Wort wie oft vorkam - mit einem Beispiel. */
    private inner class Tally {
        private val counts = sortedMapOf<String, MutableMap<String, Int>>()
        private val examples = HashMap<String, String>()

        fun add(group: String, word: String, example: String) {
            counts.getOrPut(group) { mutableMapOf() }.merge(word, 1, Int::plus)
            examples.putIfAbsent("$group/$word", example)
        }

        fun print(top: Int) {
            counts.forEach { (group, words) ->
                val sorted = words.entries.sortedByDescending { it.value }
                line("  $group (${words.values.sum()} Treffer, ${words.size} Woerter)")
                sorted.take(top).forEach { (word, count) ->
                    line("    $word: $count  (${examples["$group/$word"].orEmpty().take(EXAMPLE_LIMIT)})")
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_PAGES = 30
        const val TARGETED = 6
        const val SEED = 20_261_120
        const val SOURCE_BUDGET_MS = 240_000L
        const val PAGE_TIMEOUT_MS = 25_000L
        const val TOP_TRIGGERS = 45
        const val TOP_SUSPECTS = 40
        const val TOP_NEUTRAL = 450
        const val TITLE_ONLY_LIMIT = 40
        const val LINE_LIMIT = 400
        const val EXAMPLE_LIMIT = 60

        const val STAT_FIELDS = 5
        const val TRIED = 0
        const val READ = 1
        const val FAILED = 2
        const val NO_RECIPE = 3
        const val NO_INGREDIENTS = 4

        /**
         * Woran sich ein Verdacht festmacht - bewusst weit gefasst. Was hier trifft und nicht
         * erkannt wurde, ist kein Fehler, sondern eine Zeile zum Anschauen.
         */
        val SUSPECTS: Map<Dislike, Regex> = mapOf(
            Dislike.PILZE to "pilz|champignon|schwamm|egerling|seitling|shiitake|shitake|morchel|trueffel|portobello|" +
                "pfifferling|funghi|porcini|mushroom|enoki|shimeji|maitake|roehrling|reizker|parasol|judasohr|" +
                "mu err|totentrompete|rotkappe|hallimasch|tartufo|reherl|cremini|crimini|champis",
            Dislike.FISCH to "fisch|lachs|forelle|kabeljau|dorsch|sardelle|sardine|anchovi|pangasius|makrele|hering|" +
                "zander|egli|felchen|saibling|thon\\b|tuna|dorade|wolfsbarsch|seeteufel|scholle|seezunge|heilbutt|" +
                "karpfen|hecht|barsch|matjes|rollmops|kaviar|surimi|sushi|sashimi|stockfisch|salmon|wels\\b|" +
                "\\baal\\b|" +
                "goldbrasse|rotbarsch|seehecht|steinbutt|schwertfisch|bottarga|sprotte|renke",
            Dislike.MEERESFRUECHTE to "garnele|crevette|scampi|muschel|tintenfisch|calamari|krabbe|hummer|krebs|" +
                "meeresfrucht|meeresfruecht|auster|shrimp|prawn|langustine|languste|gamba|oktopus|octopus|pulpo|" +
                "sepia|kalmar|vongole|cozze|frutti di mare|jakobs|krake",
            Dislike.POULET to "poulet|huhn|huehn|haehnchen|hendl|pute|truthahn|gefluegel|chicken|\\bente\\b|enten|" +
                "\\bgans\\b|gaense|wachtel|stubenkueken|mistkratzer|coquelet|trute|fasan|poularde|nuggets|wings|" +
                "broiler|gueggeli|backhend",
            Dislike.RIND to "rind|kalb|beef|entrecote|siedfleisch|tatar|hohruecken|ochsen|buendnerfleisch|tafelspitz|" +
                "hackfleisch|gehacktes|faschiert|bolognese|burger|steak|gulasch|carpaccio|brasato|bresaola|" +
                "sauerbraten|rouladen|fleischvoegel|pastrami|corned|rumpsteak|\\bhuft|hacktaetschli|hackbraten",
            Dislike.SCHWEIN to "schwein|speck|schinken|bacon|pancetta|salami|chorizo|cervelat|landjaeger|prosciutto|" +
                "mortadella|lyoner|fleischkaese|kassler|haxe|spareribs|wienerli|wuerst|wurst|guanciale|coppa|rippli|" +
                "ferkel|pork|kotelett|schnitzel|leberkaese|braet|cipollata|chipolata|salsiz|saucisson|selch|" +
                "eisbein|rippchen|\\bribs\\b|schmalz|lardo|nduja|salsiccia|luganig|frankfurter|krainer|krakauer|" +
                "aufschnitt|hot ?dog|carbonara|saltimbocca|cordon bleu",
            Dislike.LAMM to "lamm|gigot|hammel|schaffleisch|zicklein|gitzi|lamb",
            Dislike.INNEREIEN to "leber|niere|nierli|kutteln|milken|zunge|herzen|\\bherz\\b|hirn|bries|kaldaunen|" +
                "beuschel|lunge|pansen|blutwurst|kalbskopf|ochsenschwanz|markbein|markknochen|trippa|foie gras",
            Dislike.ZWIEBELN to "zwiebel|schalotte|boelle|zibele|onion|cipoll|echalote|schluppen",
            Dislike.KNOBLAUCH to "knoblauch|knobli|aioli|garlic|aglio|baerlauch|knofel",
            Dislike.KORIANDER to "koriander|cilantro|coriander",
            Dislike.OLIVEN to "olive|tapenade",
            Dislike.AUBERGINE to "aubergine|melanzan|eierfrucht|baba gan|baba gha|moussaka|ratatouille|parmigiana",
            Dislike.KOHL to "kohl|kabis|wirz|wirsing|kraut\\b|coleslaw|pak choi|kimchi|chabis|romanesco|broccoli|" +
                "brokkoli|kohlsprossen",
            Dislike.SCHARF to "chili|chilli|peperoncin|jalapeno|cayenne|sambal|harissa|sriracha|tabasco|habanero|" +
                "gochujang|scharf|feurig|spicy|arrabbiata|diavol|piri|wasabi|meerrettich|currypaste|vindaloo|" +
                "pfefferschote|chipotle|szechuan|sichuan",
            Dislike.KRAEFTIGER_KAESE to "gorgonzola|roquefort|blauschimmel|schafskaese|schafkaese|limburger|harzer|" +
                "munster|taleggio|stilton|ziegen|chevre|blue cheese|blaukaese|edelpilz|cambozola|romadur|handkaese",
            Dislike.TOFU to "tofu|seitan|tempeh|fleischersatz|soja|quorn|planted|vegan|veggie|\\bvegi|jackfruit|" +
                "lupine|erbsenprotein|pflanzlich",
        ).mapValues { Regex(it.value) }

        // --- Eingebaute Seiten fuer den Lauf ohne Netz ---

        val PROBE_SOURCES = listOf(RecipeSourceCatalog.BETTY_BOSSI, RecipeSourceCatalog.ICHKOCHE)

        private fun probePage(title: String, vararg ingredients: String) =
            """<html><head><script type="application/ld+json">{"@context":"https://schema.org","@type":"Recipe",""" +
                """"name":"$title","recipeIngredient":[${ingredients.joinToString(",") { "\"$it\"" }}],""" +
                """"recipeInstructions":"Alles in der Pfanne anbraten und zum Garnieren Petersilie dazu."}""" +
                """</script></head><body></body></html>"""

        val PROBE_PAGES: Map<String, String> = mapOf(
            "https://www.bettybossi.ch/de/rezepte/rezept/a" to
                probePage("Rahmschnitzel", "4 Schweinsschnitzel", "250 g braune Egerlinge, in Scheiben", "2 dl Rahm"),
            "https://www.bettybossi.ch/de/rezepte/rezept/b" to
                probePage("Pilzrisotto", "300 g Risottoreis", "200 g Schwammerl", "1 Zwiebel, fein gehackt"),
            "https://www.ichkoche.at/c-rezept-1" to
                probePage("Flammkuchen", "1 Flammkuchenteig", "200 g Crème fraîche", "abgeriebene Zitronenrinde"),
            "https://www.ichkoche.at/d-rezept-2" to
                probePage("Linsenteller", "200 g Linsen", "1 Dose gehackte Tomaten", "Spaghetti al dente"),
        )

        val PROBE_ENTRIES: List<SampleEntry> = PROBE_PAGES.keys.mapIndexed { index, url ->
            SampleEntry(
                url = url,
                title = listOf("Rahmschnitzel", "Pilzrisotto", "Flammkuchen", "Linsenteller")[index],
                imageUrl = null,
                sourceId = if ("bettybossi" in url) "bettybossi" else "ichkoche",
            )
        }
    }
}

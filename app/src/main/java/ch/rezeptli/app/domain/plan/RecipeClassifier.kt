package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.IngredientNameNormalizer
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.Mood
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Schaetzt aus einem Rezept, wofuer es taugt.
 *
 * Quellen liefern keine Angaben wie "vegetarisch" oder "glutenfrei" in einheitlicher Form.
 * Deshalb liest die App die Zutaten selbst - Stichwort fuer Stichwort. Das ist eine
 * Heuristik und wird in der App auch nie als Gewaehr dargestellt.
 *
 * Gewichtet wird zugunsten der Vorsicht: Im Zweifel gilt ein Rezept als "nicht vegan" und
 * "enthaelt Allergen" - ein ausgeschlossenes gutes Rezept ist weniger schlimm als ein
 * eingeschlossenes schlechtes.
 */
class RecipeClassifier @Inject constructor() {
    fun classify(recipe: Recipe): RecipeTraits {
        val title = Phrase(recipe.title)
        val names = recipe.ingredients.flatMap { ingredient ->
            listOfNotNull(Phrase(ingredient.name), ingredient.canonicalName?.let(::Phrase))
        }
        // Auch was hinter dem Komma steht, gehoert zum Rezept: "z. B. Schinken oder Speckwuerfeli".
        val notes = recipe.ingredients.mapNotNull { it.note?.takeIf(String::isNotBlank)?.let(::Phrase) }
        val content = Content(ingredients = names, title = title, noted = notes)

        val ingredientWords = names.flatMapTo(mutableSetOf()) { it.words }
        val titleWords = title.words
        val all = ingredientWords + titleWords
        val everything = all + notes.flatMap { it.words }
        val tagWords = recipe.tags.flatMap(::words).toSet()
        // Die Zubereitung wird nur einmal geglaettet - sie ist der laengste Text am Rezept.
        val steps = IngredientNameNormalizer.normalize(recipe.instructions)
        val text = IngredientNameNormalizer.normalize(recipe.title + " " + recipe.tags.joinToString(" ")) + " " + steps

        // Was die Zubereitung nennt, zaehlt mit - siehe [mentionsIn]. Bei einem Rezept, das sich
        // als vegetarisch ausweist oder auf Fleischersatz baut, meint "die Wuerstchen anbraten"
        // den Ersatz: Dort zaehlen aus der Zubereitung nur die Pflanzen.
        val labels = titleWords.toSet() + tagWords
        val mentions = mentionsIn(steps)
        val meatless = labels.any(MEATLESS_LABELS::matches) ||
            content.has(SUBSTITUTES) ||
            content.plantBased.any(::namesMeat)
        val animalMentions = if (meatless) emptySet() else mentions

        val listed = animalsIn(content)
        val animals = listed + mentionedAnimals(animalMentions, known = listed)
        val hasMeat = animals.isNotEmpty() || content.real.any { OTHER_MEAT.isIn(it) }
        val hasSeafood = content.real.any { SEAFOOD.isIn(it) } || Mention.SEAFOOD in animalMentions
        val hasFishItself = content.real.any { FISH.isIn(it) } || Mention.FISH in animalMentions
        val hasFish = hasSeafood || hasFishItself
        val fromTheSea = setOfNotNull(
            Dislike.FISCH.takeIf { hasFishItself },
            Dislike.MEERESFRUECHTE.takeIf { hasSeafood },
        )
        val hasDairy = content.real.any { DAIRY.isIn(it) }
        val hasEgg = everything.any(::isEgg)
        val hasHoney = content.has(HONEY)

        val diet = when {
            hasMeat -> DietClass.FLEISCH
            hasFish -> DietClass.FISCH
            hasDairy || hasEgg || hasHoney -> DietClass.VEGETARISCH
            else -> DietClass.VEGAN
        }

        val allergens = buildSet {
            if (content.has(GLUTEN)) add(Intolerance.GLUTEN)
            if (hasDairy) add(Intolerance.LAKTOSE)
            if (content.has(NUTS)) add(Intolerance.NUESSE)
            if (hasEgg) add(Intolerance.EIER)
            if (content.has(SOY)) add(Intolerance.SOJA)
            if (content.has(SEAFOOD) || Mention.SEAFOOD in animalMentions) add(Intolerance.MEERESFRUECHTE)
        }

        // Suess und herzhaft zaehlen nur an den Zutaten - der Titel entscheidet in CourseRules.
        val sweet = ingredientWords.count { word -> SWEET.any { word.contains(it) } }
        val savory = ingredientWords.count { word ->
            isMeatWord(word) ||
                FISH.matches(word) ||
                SAVORY.any { word.contains(it) } ||
                VEGETABLES.any { word.contains(it) }
        }

        return RecipeTraits(
            diet = diet,
            allergens = allergens,
            moods = moodsOf(recipe, all, titleWords.toSet(), diet, text),
            requiredAppliances = appliancesOf(text),
            lowCarb = all.none { word -> CARBS.any { word.contains(it) } },
            dislikes = dislikesOf(content, animals + fromTheSea, ingredientWords, labels, mentions),
            course = CourseRules.of(titleWords, tagWords, sweet, savory),
            cuisines = CuisineRules.of(titleWords, tagWords),
            words = everything,
        )
    }

    /**
     * Schaetzt ein Rezept nur aus seinem Titel ein.
     *
     * Mehr als den Titel kennt die App von einer Wischkarte nicht, solange die Rezeptseite
     * nicht geladen ist. Das reicht, um Offensichtliches auszusortieren ("Lachsfilet" fuer
     * jemanden, der keinen Fisch mag) - aber nie, um etwas zuzusagen.
     */
    fun classifyTitle(title: String): RecipeTraits = classify(Recipe(title = title))

    /**
     * Was in einem Rezept steht: die Zutaten, der Titel und die Notizen zu den Zutaten.
     *
     * [real] laesst weg, was als pflanzlicher Ersatz ausgeschrieben ist: Eine "vegane
     * Bratwurst" ist keine Wurst und "Soja-Geschnetzeltes" kein Fleisch.
     */
    private class Content(val ingredients: List<Phrase>, title: Phrase, noted: List<Phrase>) {
        val everything: List<Phrase> = ingredients + title + noted
        val real: List<Phrase> = everything.filterNot { PLANT_MARKER.isIn(it) }
        val plantBased: List<Phrase> = everything.filter { PLANT_MARKER.isIn(it) }

        fun has(set: WordSet): Boolean = everything.any { set.isIn(it) }
    }

    /**
     * Welche Tiere im Rezept stecken - als die Abneigungen, die dazu gehoeren.
     *
     * Hackfleisch ohne naehere Angabe ist hierzulande Rind, in Deutschland und Oesterreich oft
     * mit Schwein gemischt. Wer eines von beiden nicht mag, will es deshalb nicht sehen.
     */
    private fun animalsIn(content: Content): Set<Dislike> = buildSet {
        content.real.forEach { phrase ->
            val poultry = POULTRY.isIn(phrase)
            val beef = BEEF.isIn(phrase)
            val pork = PORK.isIn(phrase)
            val lamb = LAMB.isIn(phrase)
            if (poultry) add(Dislike.POULET)
            if (beef) add(Dislike.RIND)
            if (pork) add(Dislike.SCHWEIN)
            if (lamb) add(Dislike.LAMM)
            if (OFFAL.isIn(phrase)) add(Dislike.INNEREIEN)
            if (isMinced(phrase) && !poultry && !beef && !pork && !lamb && !FISH.isIn(phrase)) {
                add(Dislike.RIND)
                add(Dislike.SCHWEIN)
            }
        }
        // "Tatar" als Zutat ist rohes Rindfleisch - im Titel ist es bloss eine Zubereitungsart.
        if (content.ingredients.any { BEEF_AS_INGREDIENT.isIn(it) && !PLANT_MARKER.isIn(it) }) add(Dislike.RIND)
    }

    /**
     * "Hackfleisch", "Hackbraten", "Faschiertes" - und "Gehacktes", wenn es als Hauptwort
     * dasteht. "Gehacktes Basilikum" ist dagegen bloss gehackt.
     */
    private fun isMinced(phrase: Phrase): Boolean {
        if (MINCED.isIn(phrase)) return true
        val index = phrase.words.indexOf(MINCED_NOUN)
        if (index < 0) return false
        val next = phrase.words.getOrNull(index + 1)
        return next == null || next in AFTER_A_NOUN
    }

    /**
     * Was das Rezept enthaelt, das jemand nicht moegen koennte.
     *
     * [animals] sind die Tiere, Fisch und Meeresfruechte - sie stehen schon fest. [labels] sind
     * Titel und Schlagworte: Nur dort steht, ob ein Rezept als vegan oder scharf ausgeschrieben
     * ist. [mentions] ist, was nur die Zubereitung nennt.
     */
    private fun dislikesOf(
        content: Content,
        animals: Set<Dislike>,
        ingredientWords: Set<String>,
        labels: Set<String>,
        mentions: Set<Mention>,
    ): Set<Dislike> = buildSet {
        addAll(animals)
        if (hasMushrooms(content, mentions)) add(Dislike.PILZE)
        if (content.has(ONIONS) || Mention.ONION in mentions) add(Dislike.ZWIEBELN)
        if (content.has(GARLIC) || Mention.GARLIC in mentions) add(Dislike.KNOBLAUCH)
        if (content.has(CORIANDER) || Mention.CORIANDER in mentions) add(Dislike.KORIANDER)
        if (content.has(OLIVES) || Mention.OLIVE in mentions) add(Dislike.OLIVEN)
        if (content.has(AUBERGINE) || Mention.AUBERGINE in mentions) add(Dislike.AUBERGINE)
        if (content.has(CABBAGE) || Mention.CABBAGE in mentions) add(Dislike.KOHL)
        // "Scharf" als Wort zaehlt nur am Namen ("Senf scharf") und am Etikett - in einer Notiz
        // heisst es meist "scharf angebraten".
        if (content.has(HOT_FOOD) || (ingredientWords + labels).any(HOT_LABELS::matches)) add(Dislike.SCHARF)
        if (content.has(STRONG_CHEESE)) add(Dislike.KRAEFTIGER_KAESE)

        val substitute = content.has(SUBSTITUTES) || content.plantBased.any(::namesMeat)
        if (substitute) add(Dislike.TOFU)
        if (substitute || labels.any { it.startsWith("vegan") }) add(Dislike.VEGAN)
    }

    /** Trueffel sind Pilze - ausser sie sind aus Schokolade. */
    private fun hasMushrooms(content: Content, mentions: Set<Mention>): Boolean =
        content.has(MUSHROOMS) ||
            Mention.MUSHROOM in mentions ||
            ((content.has(TRUFFLES) || Mention.TRUFFLE in mentions) && !content.has(CONFECTIONERY))

    /**
     * Was die Zubereitung an Zutaten nennt.
     *
     * Die Zutatenliste einer Seite ist nicht immer vollstaendig. An den echten Seiten gemessen:
     * Bei rund jeder fuenfzigsten steht etwas nur in der Zubereitung - "Gebackene Champignons"
     * mit vier verstuemmelten Zutaten, waehrend der Text Zwiebel und Sardellen nennt, oder
     * Backerbsen, die "in Schmalz" gebacken werden. Wer keinen Fisch isst, will das nicht sehen.
     *
     * Gelesen wird mit Bedacht, denn in einer Zubereitung steht vieles, was nicht im Topf landet:
     *
     * - Nur Fleisch, Fisch, Meeresfruechte und die Lebensmittel unter "Mag ich nicht". Nicht
     *   Gluten, Milch, Eier oder Nuesse - dort stand fast immer ein Serviervorschlag ("dazu
     *   Baguette") - und nicht "scharf", das im Text "scharf anbraten" heisst.
     * - Nur einzelne, bestimmte Woerter. Gerichte ("wie ein Schnitzel panieren"), Geraete
     *   ("Knoblauchpresse") und Vergleiche ("in walnussgrosse Stuecke") zaehlen nicht.
     */
    private fun mentionsIn(steps: String): Set<Mention> {
        if (steps.isBlank()) return emptySet()
        var bits = 0
        // [steps] ist schon geglaettet: Kleinbuchstaben, Woerter durch je ein Leerzeichen getrennt.
        steps.split(' ').forEach { word -> if (word.length >= MIN_WORD) bits = bits or mentionBits(word) }
        if (bits == 0) return emptySet()
        return Mention.entries.filterTo(mutableSetOf()) { bits and (1 shl it.ordinal) != 0 }
    }

    /**
     * Die Tiere aus der Zubereitung, als Abneigungen. Hackfleisch ohne Tierangabe zaehlt wie in
     * der Zutatenliste als Rind und Schwein - ausser das Tier steht schon fest ([known]).
     */
    private fun mentionedAnimals(mentions: Set<Mention>, known: Set<Dislike>): Set<Dislike> = buildSet {
        if (Mention.POULTRY in mentions) add(Dislike.POULET)
        if (Mention.BEEF in mentions) add(Dislike.RIND)
        if (Mention.PORK in mentions) add(Dislike.SCHWEIN)
        if (Mention.LAMB in mentions) add(Dislike.LAMM)
        if (Mention.OFFAL in mentions) add(Dislike.INNEREIEN)
        if (Mention.MINCED in mentions && isEmpty() && known.none { it in MEAT_ANIMALS }) {
            add(Dislike.RIND)
            add(Dislike.SCHWEIN)
        }
    }

    /** Was sich aus der Zubereitung herauslesen laesst - jedes haengt an einer der Wortlisten. */
    private enum class Mention {
        POULTRY,
        BEEF,
        PORK,
        LAMB,
        OFFAL,
        MINCED,
        FISH,
        SEAFOOD,
        MUSHROOM,
        TRUFFLE,
        ONION,
        GARLIC,
        CORIANDER,
        OLIVE,
        AUBERGINE,
        CABBAGE,
    }

    /** Nennt der Name ein Fleischprodukt? Zusammen mit "vegan" ist es dann ein Fleischersatz. */
    private fun namesMeat(phrase: Phrase): Boolean =
        POULTRY.isIn(phrase) ||
            BEEF.isIn(phrase) ||
            PORK.isIn(phrase) ||
            LAMB.isIn(phrase) ||
            MINCED.isIn(phrase) ||
            MEAT_DISHES.isIn(phrase)

    private fun isMeatWord(word: String): Boolean =
        POULTRY.matches(word) ||
            BEEF.matches(word) ||
            PORK.matches(word) ||
            LAMB.matches(word) ||
            MINCED.matches(word) ||
            OTHER_MEAT.matches(word)

    private fun moodsOf(
        recipe: Recipe,
        all: Set<String>,
        title: Set<String>,
        diet: DietClass,
        text: String,
    ): Set<Mood> = buildSet {
        val minutes = recipe.prepTimeMinutes
        if (minutes != null && minutes <= QUICK_MINUTES) add(Mood.SCHNELL)

        val heavy = all.any { word -> HEAVY.any { word.contains(it) } } || FRIED.any { text.contains(it) }
        val vegetables = all.count { word -> VEGETABLES.any { word.contains(it) } }
        if (!heavy && vegetables >= 2) add(Mood.LEICHT)

        if (title.any { word -> FAMILY.any { word.contains(it) } }) add(Mood.FAMILIE)
        if (title.any { word -> COMFORT.any { word.contains(it) } }) add(Mood.COMFORT)
        if (title.any { word -> TAKEAWAY.any { word.contains(it) } }) add(Mood.FAKEAWAY)

        val gutHits = all.count { word -> GUT.any { word.contains(it) } }
        if (gutHits >= 2) add(Mood.DARMFREUNDLICH)

        val proteinHits = all.count { word -> PROTEIN.any { word.contains(it) } }
        if (proteinHits >= 1 && (diet == DietClass.FLEISCH || diet == DietClass.FISCH || proteinHits >= 2)) {
            add(Mood.PROTEIN)
        }
    }

    private fun appliancesOf(text: String): Set<Appliance> = buildSet {
        if (OVEN.any { text.contains(it) }) add(Appliance.BACKOFEN)
        if (text.contains("mikrowelle")) add(Appliance.MIKROWELLE)
        if (text.contains("heissluftfritteuse") || text.contains("airfryer") || text.contains("air fryer")) {
            add(Appliance.HEISSLUFT)
        }
        if (text.contains("slow cooker") || text.contains("schongarer") || text.contains("langsamkocher")) {
            add(Appliance.SLOWCOOKER)
        }
        if (text.contains("reiskocher")) add(Appliance.REISKOCHER)
        if (STEAMER.any { text.contains(it) }) add(Appliance.DAMPFGARER)
        // Alles, was keines der Geraete verlangt, geht auf dem Herd.
        if (isEmpty() || STOVE.any { text.contains(it) }) add(Appliance.HERD)
    }

    /**
     * Eier stehen als ganzes Wort da ("Ei", "Eier") - ein blosses "ei" im Wort waere bei
     * "Reis" und "Weizen" ein Fehlalarm. Zusammensetzungen mit "Eier-" zaehlen mit.
     */
    private fun isEgg(word: String): Boolean =
        word in EGG || (word.startsWith("eier") && EGG_LOOKALIKES.none { word.startsWith(it) })

    private fun words(text: String): List<String> =
        IngredientNameNormalizer.normalize(text).split(' ').filter { it.length >= 2 }

    private companion object {
        const val QUICK_MINUTES = 30

        val MEAT_ANIMALS = setOf(Dislike.POULET, Dislike.RIND, Dislike.SCHWEIN, Dislike.LAMM)

        /** Kuerzere Woerter der Zubereitung sagen nichts ("in", "g", "l"). */
        const val MIN_WORD = 3

        /** So viele Woerter merkt sich [mentionBits] hoechstens. */
        const val MAX_REMEMBERED_WORDS = 20_000

        /**
         * Was ein Wort der Zubereitung bedeutet - gemerkt, weil dieselben Woerter in jedem Rezept
         * wiederkehren. Ohne das kostete eine grosse Sammlung beim Einstufen Sekunden.
         */
        private val remembered = ConcurrentHashMap<String, Int>()

        private val mentionSets: List<Pair<Mention, WordSet>> by lazy {
            listOf(
                Mention.POULTRY to POULTRY,
                Mention.BEEF to BEEF,
                Mention.PORK to PORK,
                Mention.LAMB to LAMB,
                Mention.OFFAL to OFFAL,
                Mention.MINCED to MINCED,
                Mention.FISH to FISH,
                Mention.SEAFOOD to SEAFOOD,
                Mention.MUSHROOM to MUSHROOMS,
                Mention.TRUFFLE to TRUFFLES,
                Mention.ONION to ONIONS,
                Mention.GARLIC to GARLIC,
                Mention.CORIANDER to CORIANDER,
                Mention.OLIVE to OLIVES,
                Mention.AUBERGINE to AUBERGINE,
                Mention.CABBAGE to CABBAGE,
            )
        }

        /** Die Bedeutungen eines Worts der Zubereitung, als Bits nach [Mention]. */
        fun mentionBits(word: String): Int {
            remembered[word]?.let { return it }
            val bits = if (NOT_FOOD_IN_TEXT.matches(word)) {
                0
            } else {
                mentionSets.fold(0) { found, (mention, set) ->
                    if (set.matches(word)) found or (1 shl mention.ordinal) else found
                }
            }
            if (remembered.size >= MAX_REMEMBERED_WORDS) remembered.clear()
            remembered[word] = bits
            return bits
        }

        /**
         * Woerter der Zubereitung, in denen ein Lebensmittel steckt und die doch keines sind:
         * Vergleiche ("walnussgross", "lachsfarben"), Geraete ("Knoblauchpresse", "Fischheber"),
         * Ersatz und Aroma - und ein paar Tunwoerter und Redensarten ("das Fleisch braet",
         * "herausfischen", "zergeht auf der Zunge").
         */
        val NOT_FOOD_IN_TEXT = WordSet(
            "*gross, *grosse, *grossen, *grosser, *grosses, *artig, *artige, *artigen, *ähnlich, *ähnliche, " +
                "*ähnlichen, *farben, *farbene, *farbenen, *farbig, *farbige, *hacker, *presse, *messer, " +
                "*klopfer, *schere, *heber, *zange, *gabel, *besteck, *brett, *hobel, *ersatz, *alternative, " +
                "*aroma, *geschmack, =brät, =anbrät, =zunge, speckig*, herausfischen, rausfischen, auffischen, " +
                "abfischen, herausgefischt",
        )

        // ------------------------------------------------------------------------------
        // Die Wortlisten. Sie sind an echten Rezeptseiten der Quellen gemessen (siehe
        // FilterAuditTest): Was dort als Treffer oder als Fehlalarm auffiel, steht hier.
        // ------------------------------------------------------------------------------

        /** So weist sich ein Rezept in Titel oder Schlagworten als fleischlos aus. */
        val MEATLESS_LABELS = WordSet(
            "vegan*, veggie*, =vegi, vegi*, vegetarisch*, vegetarian, pflanzlich*, fleischlos*, plantbased",
        )

        /** Als pflanzlicher Ersatz ausgeschrieben - dann zaehlt das Tier im Namen nicht. */
        val PLANT_MARKER = WordSet(
            "vegan*, veggie*, =vegi, vegi*, vegetarisch*, pflanzlich*, planted*, plantbased, fleischlos*, " +
                "*ersatz, soja*, tofu*, seitan*",
        )

        val POULTRY = WordSet(
            "poulet, huhn, hühn, hähnchen, hänchen, hendl, pute, truthahn, trute, geflügel, chicken, =ente, " +
                "=enten, enten*, knusperente, pekingente, wildente, flugente, mastente, bauernente, barbarie, " +
                "=gans, gänse*, *gans, wachtel, fasan, stubenküken, mistkratzer, coquelet, poularde, =kapaun, " +
                "güggeli, =pollo, =broiler, =taube, tauben*, =poussin",
            // Das Ei eines Huhns ist kein Huhn - der Huehnereintopf dagegen schon, darum als ganze Woerter.
            except = "wachtelei, gänseblümchen, =hühnerei, =hühnereier, =hühnereiern, hühnereiweiss, " +
                "hühnereigelb, =entenei, =enteneier, =gänseei, =gänseeier, =straussenei, computer",
        )

        val BEEF = WordSet(
            "=rind, rinds*, rinder*, rindfleisch, *rind, kalb*, *kalb, beef, entrecôte, siedfleisch, hohrücken, " +
                "ochsen, =ochse, bündnerfleisch, tafelspitz, =huft, huft*, *huft, rumpsteak, ribeye, =boeuf, " +
                "bresaola, pastrami, mostbröckli, bindenfleisch, vitello, wagyu, =angus, tbone, sauerbraten, " +
                "beiried, rostbraten, ossobuco",
            // "Ochsenaugen" sind Spiegeleier.
            except = "ochsenherz, ochsenauge, kochsendung, tamarind, tomate",
        )

        /** Gilt nur als Zutat - im Titel ist ein "Tatar" oft aus Gemuese oder Fisch. */
        val BEEF_AS_INGREDIENT = WordSet("=tatar, =tartar, rindstatar")

        val PORK = WordSet(
            "schwein, speck, schinken, bacon, pancetta, salami, chorizo, cervelat, landjäger, prosciutto, " +
                "mortadella, lyoner, fleischkäse, leberkäse, kassler, kasseler, =haxe, =haxen, =haxn, =stelze, " +
                "=stelzen, spareribs, =ribs, rippli, rippchen, wienerli, würst, wurst, guanciale, =coppa, =lardo, " +
                "lardons, =brät, *brät, brätkügeli, brätchügeli, brätmasse, cipollata, chipolata, salsiz, " +
                "saucisson, saucisse, geselcht, eisbein, =schmalz, griebenschmalz, nduja, salsiccia, luganig, " +
                "frankfurterli, krainer, krakauer, debreziner, aufschnitt, hotdog, =pork, pulledpork, =mett, " +
                "cabanossi, kabanossi, =serrano, =jamon, ferkel",
            // Eine Gefluegelwurst ist kein Schwein, und die Palatschinke ein Pfannkuchen.
            except = "palatschinke, speckstein, poulet, geflügel, huhn, hühn, hähnchen, pute, trute, truthahn, chicken",
        )

        val LAMB = WordSet(
            "=lamm, lamm*, *lamm, gigot, hammel, =schaf, schaffleisch, zicklein, gitzi, =lamb, =mutton",
            except = "=flamm, osterlamm",
        )

        val OFFAL = WordSet(
            "=leber, leber*, *leber, =niere, =nieren, =nierli, =nierchen, kalbsniere, kalbsnierli, schweinsniere, " +
                "lammniere, rindsniere, kutteln, =kuttel, kaldaunen, =pansen, milken, =bries, *bries, rindszunge, " +
                "kalbszunge, schweinszunge, ochsenzunge, lammzunge, =zunge, zungenwurst, kalbsherz, rinderherz, " +
                "rindsherz, schweineherz, schweinsherz, hühnerherz, hähnchenherz, pouletherz, geflügelherz, " +
                "entenherz, putenherz, =hirn, kalbshirn, schweinshirn, beuschel, kalbslunge, =lunge, foiegras, " +
                "kalbskopf, ochsenmaul, blutwurst, blunzen, =blunze, saumagen, hühnermagen, entenmagen, " +
                "kalbsmagen, muskelmagen, =innereien, trippa, =haggis",
            except = "leberkäse, kleber, lebertran",
        )

        /** Hackfleisch in seinen Schreibweisen - ohne das blosse "gehackt". */
        val MINCED = WordSet("hack*, faschiert*, =haschee", except = "=hacken, =hacke, =hackt, hackbrett")
        const val MINCED_NOUN = "gehacktes"
        val AFTER_A_NOUN = setOf("mit", "vom", "von", "und", "gemischt", "an", "auf", "im", "in", "zum", "zu", "oder")

        /** Gerichte und Produkte, die nach Fleisch heissen - fuer "vegane Bratwurst" und Verwandte. */
        val MEAT_DISHES = WordSet("schnitzel, steak, burger, nuggets, geschnetzeltes, gyros, kebab, döner, frikadelle")

        /** Fleisch, das zu keiner der Abneigungen gehoert - es zaehlt fuer "vegetarisch". */
        val OTHER_MEAT = WordSet(
            "fleisch*, *fleisch, =wild, hirsch*, =reh, rehrücken, rehschnitzel, rehragout, rehfilet, rehkeule, " +
                "rehpfeffer, rehnuss, rehmedaillon, rehgulasch, rehbraten, kaninchen, =hase, hasenpfeffer, " +
                "hasenrücken, hasenkeule, hasenfilet, hasenragout, =gams, gams*, pferde*, kebab, =kebap, döner, " +
                "gyros, schnitzel, steak, gulasch, kotelett, geschnetzeltes, bratenfond, bratensauce, " +
                "bratensosse, bratenjus, =jus, demiglace, gelatine, lungenbraten, cordonbleu, saltimbocca, " +
                "frikadelle, bulette, =köfte, kofta, cevapcici, souvlaki, shawarma, sucuk, merguez, piccata",
            except = "fruchtfleisch, kürbisfleisch, kokosfleisch, kokosnussfleisch, tomatenfleisch, avocadofleisch, " +
                "mangofleisch, melonenfleisch, krebsfleisch, krabbenfleisch, muschelfleisch, hummerfleisch, " +
                "fischfleisch, orangenfleisch, olivenfleisch, pfirsichfleisch, birnenfleisch, apfelfleisch, " +
                "zitronenfleisch, papayafleisch, ananasfleisch, grapefruitfleisch, fleischtomate, fleischig, " +
                "hirschhornsalz, kohlrabischnitzel, sellerieschnitzel, gemüseschnitzel, käseschnitzel, " +
                "tofuschnitzel, sojaschnitzel, kürbisschnitzel, auberginenschnitzel, zucchinischnitzel, " +
                "blumenkohlschnitzel, halloumischnitzel, pilzschnitzel, parasolschnitzel, fischschnitzel, " +
                "lachsschnitzel, fischsteak, lachssteak, kürbissteak, blumenkohlsteak, selleriesteak, " +
                "tofusteak, sojasteak, gemüsesteak, auberginensteak, melonensteak, kohlrabisteak, " +
                "halloumisteak, krautsteak, fischgulasch, schwammerlgulasch, pilzgulasch, kartoffelgulasch, " +
                "erdäpfelgulasch, gemüsegulasch, tofugulasch, kürbisgulasch, bohnengulasch, linsengulasch, " +
                "sojagulasch, zucchinigulasch, pilzgeschnetzeltes, sojageschnetzeltes, tofugeschnetzeltes",
        )

        val FISH = WordSet(
            "fisch, lachs, =thon, thon*, =tuna, forelle, kabeljau, dorsch, sardelle, sardine, anchovi, pangasius, " +
                "makrele, hering, matjes, zander, egli*, =felchen, felchen*, saibling, dorade, =dorada, goldbrasse, " +
                "meerbrasse, =brasse, =brassen, =barsch, barschfilet, wolfsbarsch, rotbarsch, seebarsch, " +
                "viktoriabarsch, flussbarsch, zackenbarsch, seeteufel, scholle, seezunge, rotzunge, heilbutt, " +
                "steinbutt, =butt, karpfen, =hecht, hecht*, seehecht, tilapia, =wels, welsfilet, =aal, räucheraal, " +
                "kaviar, =rogen, surimi, sushi, sashimi, bacalhau, bacalao, =salmon, bottarga, sprotte, =renke, " +
                "renken*, rollmops, =lox, gravlax, =graved, bonito, =dashi",
            // Sushi-Reis ist Reis.
            except = "fischerl, tintenfisch, lachsschinken, flachs, auberginenkaviar, veggiekaviar, algenkaviar, " +
                "sushireis, sushiessig, sushimatte, sushiingwer, sushinori, spezifisch, pazifisch",
        )

        val SEAFOOD = WordSet(
            "garnele, crevette, scampi, =scampo, muschel, tintenfisch, calamar, =kalmar, kalmare, krabbe, hummer, " +
                "krebs, meeresfrücht, meeresfrucht, =auster, austern*, shrimp, prawn, langustine, languste, gamba, " +
                "oktopus, octopus, pulpo, =sepia, sepia*, vongole, =cozze, fruttidimare, =krake, kraken*, seeigel, " +
                "=moules, kaisergranat",
            except = "austernpilz, austernseitling, =muscheli, muschelnudel, muschelteigwaren, muschelpasta",
        )

        val MUSHROOMS = WordSet(
            "pilz, champignon, schwammerl, eierschwamm, eierschwämm, stockschwämm, =schwämme, =schwämmli, " +
                "egerling, seitling, shiitake, shitake, morchel, portobello, pfifferling, funghi, =fungi, porcini, " +
                "mushroom, enoki, shimeji, maitake, röhrling, reizker, parasol, judasohr, muerr, totentrompete, " +
                "herbsttrompete, rotkappe, hallimasch, reherl, cremini, crimini, =champis, bovist, pioppini, " +
                "kaiserling, igelstachelbart, =cepes, girolle, =nameko",
            except = "edelpilz",
        )
        val TRUFFLES = WordSet("trüffel, tartufo, tartufi")
        val CONFECTIONERY = WordSet("schoko, kuvertüre, kakao, praline, nougat, marzipan")

        val ONIONS = WordSet(
            "zwiebel, schalotte, =bölle, böllen*, =zibele, cipolla, cipolle, cipollotti, =onion, =onions, " +
                "echalote, schluppe",
            except = "cipollata",
        )

        /** Baerlauch zaehlt mit: Er schmeckt nach Knoblauch und heisst nicht umsonst wilder Knoblauch. */
        val GARLIC = WordSet("knoblauch, knobli, aioli, =aglio, garlic, bärlauch, knofel, =knofi")
        val CORIANDER = WordSet("koriander, cilantro, coriander")

        /** Olivenoel ist keine Olive. */
        val OLIVES = WordSet("olive, tapenade", except = "öl, oil")
        val AUBERGINE = WordSet("aubergine, melanzan, eierfrucht, eggplant, babaganoush, babaghanoush, moussaka")

        /**
         * Kohl im Sinn von "Kohl & Rosenkohl": Kopfkohl, Blattkohl, Sauerkraut. Blumenkohl,
         * Brokkoli und Kohlrabi sind eigene Gemuese und zaehlen nicht dazu.
         */
        val CABBAGE = WordSet(
            "=kohl, kabis, chabis, rosenkohl, weisskohl, rotkohl, grünkohl, spitzkohl, chinakohl, schwarzkohl, " +
                "federkohl, palmkohl, wirsing, wirz, sauerkraut, rotkraut, weisskraut, blaukraut, spitzkraut, " +
                "=kraut, krautsalat, krautwickel, krautfleckerl, krautstrudel, krautkopf, krautroulade, " +
                "krautkrapfen, kohlroulade, kohlsprosse, kohlsuppe, kohleintopf, kohlgemüse, kohlblatt, " +
                "kohlblätter, kohlwickel, kohlkopf, coleslaw, kimchi, pakchoi, bokchoy, cavolonero",
        )

        val HOT_FOOD = WordSet(
            "chili, chilli, peperoncin, jalapeno, cayenne, sambal, harissa, sriracha, tabasco, habanero, " +
                "gochujang, gochugaru, wasabi, pfefferschote, chipotle, piripiri, =piri, currypaste, vindaloo, " +
                "arrabbiata, arrabiata, =diavolo, =diavola, birdseye, kimchi, nduja",
        )
        val HOT_LABELS = WordSet(
            "=scharf, =scharfe, =scharfer, =scharfes, =scharfen, =scharfem, extrascharf, feurig*, =spicy, " +
                "=piccante, =picante",
        )

        val STRONG_CHEESE = WordSet(
            "ziegen*, gorgonzola, roquefort, blauschimmel, edelpilz, bluecheese, stilton, limburger, harzer, " +
                "=munster, munsterkäse, taleggio, romadur, handkäse, chevre, schafskäse, schafkäse, cambozola, " +
                "bavariablu, danablu, cabrales, fourme, epoisses, weisslacker, quargel, =schimmelkäse, " +
                "edelschimmel, grünschimmel, blaukäse",
            except = "ziegenfleisch",
        )

        /** Tofu und Fleischersatz - nicht aber Sojasauce oder Sojamilch. */
        val SUBSTITUTES = WordSet(
            "tofu, seitan, tempeh, fleischersatz, sojageschnetzeltes, sojaschnetzel, sojagranulat, sojahack, " +
                "sojamedaillon, sojawürfel, quorn, =planted, veggieburger, vegiburger, =tvp, erbsenprotein, " +
                "hackersatz",
        )

        val DAIRY = WordSet(
            "milch, rahm, sahne, butter, käse, quark, joghurt, jogurt, yoghurt, yogurt, mozzarella, parmesan, " +
                "parmigiano, =grana, gruyère, greyerzer, sbrinz, mascarpone, ricotta, cremefraiche, =fraiche, " +
                "cremedouble, emmentaler, =feta, feta*, *feta, halloumi, schmand, kefir, raclette, gorgonzola, " +
                "pecorino, burrata, =obers, *obers, =topfen, topfen*, *topfen, =skyr, =ghee, molke, cheddar, " +
                "camembert, =brie, gouda, tilsiter, appenzeller, vacherin, reblochon, taleggio, roquefort, stilton, " +
                "provolone, scamorza, fontina, manchego, paneer, labneh, tzatziki, tsatsiki, =cremefine",
            except = "kokos, hafer, mandel, soja, reismilch, reisdrink, erdnuss, kakaobutter, vegan, cashew, shea, " +
                "nussmilch, butternuss, butternut, butterbohne, leberkäse, fleischkäse, stopfen",
        )

        /** Die Honigmelone heisst nur so. */
        val HONEY = WordSet("honig", except = "honigmelone, honigpomelo, honigtomate")

        val EGG = setOf(
            "ei",
            "eier",
            "eiern",
            "eigelb",
            "eigelbe",
            "eiweiss",
            "eiweisse",
            "eidotter",
            "eischnee",
            "spiegelei",
            "spiegeleier",
            "ruehrei",
            "ruehreier",
            "omelette",
            "omeletten",
            "omelett",
            "mayonnaise",
            "mayo",
        )

        /** Beginnt mit "Eier", ist aber keines: Eierschwaemme sind Pilze, die Eierfrucht eine Aubergine. */
        val EGG_LOOKALIKES = listOf("eierschw", "eierfrucht")

        val GLUTEN = WordSet(
            "mehl, weizen, nudel, pasta, spaghetti, penne, hörnli, brot, semmel, couscous, bulgur, gerste, roggen, " +
                "dinkel, tortilla, =teig, teig*, *teig, gnocchi, lasagne, toast, brötchen, =bier, bier*, *bier, " +
                "knödel, spätzle, tagliatelle, fusilli, maccheroni, makkaroni, ravioli, tortelloni, tortellini, " +
                "panade, zwieback, wrap, croutons, sojasauce, seitan, griess, baguette, brösel, kadayif, =panko, " +
                "panko*, cracker, =zopf, croissant, gipfeli, brezel, laugen*, biskuit, =udon, =ramen, ramen*, =mie, " +
                "hafer, =malz, malz*, farfalle, linguine, rigatoni, orecchiette, cannelloni, =orzo, pappardelle, " +
                "grünkern, =emmer, einkorn, kamut, filo*, yufka, =pita, =naan, chapati, =bun, =buns, keks, guetzli, " +
                "waffel, spekulatius, lebkuchen",
            except = "buchweizen, mais, kichererbsen, kartoffel, reismehl, mandel, glutenfrei, mehlig, polenta, " +
                "kokos, johannisbrot, guarkern, linsenmehl, sojamehl, tapioka, maniok, reisnudel, glasnudel, " +
                "reispapier, reiswaffel, maiswaffel, kastanienmehl, *gewürz, *gewürzmischung",
        )

        /** Die Kalbsnuss ist ein Stueck Fleisch, die Butternuss ein Kuerbis. */
        val NUTS = WordSet(
            "nuss, nüsse, mandel, cashew, pistazie, pecan, pekan, marzipan, nutella, pinienkern, macadamia, " +
                "nougat, krokant, gianduja",
            except =
                "muskat, kokos, kalbsnuss, schweinsnuss, rindsnuss, rehnuss, lammnuss, nussschinken, butternuss, " +
                    "genuss",
        )

        /** "Tamari" als ganzes Wort - die Tamarinde ist etwas anderes. */
        val SOY = WordSet("soja, tofu, edamame, tempeh, =miso, miso*, =tamari, tamarisauce, =shoyu, =natto, teriyaki")

        val CARBS = listOf(
            "nudel",
            "pasta",
            "spaghetti",
            "penne",
            "reis",
            "kartoffel",
            "brot",
            "mehl",
            "zucker",
            "couscous",
            "bulgur",
            "teig",
            "gnocchi",
            "polenta",
            "hoernli",
            "tortilla",
            "knoedel",
            "toast",
            "haerdoepfel",
        )
        val HEAVY = listOf("sahne", "rahm", "butter", "speck", "bacon", "mascarpone", "paniermehl", "frittier", "oel")
        val FRIED = listOf("frittier", "paniert", "ausbacken", "in oel schwimmend")
        val VEGETABLES = listOf(
            "tomate",
            "zucchini",
            "zucchetti",
            "paprika",
            "peperoni",
            "karotte",
            "rueebli",
            "broccoli",
            "brokkoli",
            "spinat",
            "salat",
            "gurke",
            "lauch",
            "kohl",
            "kabis",
            "aubergine",
            "fenchel",
            "kuerbis",
            "bohnen",
            "erbsen",
            "champignon",
            "pilz",
            "sellerie",
            "mangold",
            "spargel",
            "randen",
        )
        val FAMILY = listOf(
            "pasta",
            "spaghetti",
            "hoernli",
            "gnocchi",
            "pizza",
            "burger",
            "gratin",
            "auflauf",
            "nuggets",
            "lasagne",
            "bolognese",
            "carbonara",
            "fischstaebchen",
            "geschnetzelt",
            "roesti",
            "kartoffelstock",
            "pfannkuchen",
            "crepes",
            "omelette",
            "macaroni",
        )
        val COMFORT = listOf(
            "eintopf",
            "suppe",
            "auflauf",
            "gratin",
            "braten",
            "risotto",
            "gulasch",
            "ragout",
            "schmorbraten",
            "chili",
            "curry",
            "kartoffelstock",
            "lasagne",
            "cremig",
            "fondue",
        )
        val TAKEAWAY = listOf(
            "burger",
            "pizza",
            "curry",
            "thai",
            "kebab",
            "doener",
            "wrap",
            "burrito",
            "taco",
            "nasi",
            "ramen",
            "sushi",
            "bowl",
            "nuggets",
            "gebratener",
            "tikka",
            "korma",
            "gyoza",
            "falafel",
            "teriyaki",
            "satay",
            "quesadilla",
            "fajita",
            "shawarma",
            "pommes",
            "wings",
        )
        val GUT = listOf(
            "linsen",
            "kichererbse",
            "bohnen",
            "vollkorn",
            "haferflocken",
            "joghurt",
            "kefir",
            "sauerkraut",
            "chicoree",
            "artischocke",
            "lauch",
            "zwiebel",
            "knoblauch",
            "fenchel",
            "hafer",
            "quinoa",
            "leinsamen",
            "chia",
            "banane",
            "beeren",
            "spargel",
            "pastinake",
            "ingwer",
        )
        val PROTEIN = listOf(
            "huhn",
            "haehnchen",
            "poulet",
            "thunfisch",
            "lachs",
            "quark",
            "linsen",
            "kichererbse",
            "tofu",
            "tempeh",
            "rind",
            "hack",
            "pute",
            "truthahn",
            "eier",
            "garnele",
            "crevette",
            "skyr",
            "edamame",
            "bohnen",
            "kabeljau",
            "forelle",
            "schwein",
        )
        val SWEET = listOf(
            "zucker",
            "honig",
            "ahornsirup",
            "schokolade",
            "kuvertuere",
            "kakao",
            "vanille",
            "marzipan",
            "nutella",
            "konfituere",
            "marmelade",
            "kondensmilch",
            "karamell",
            "caramel",
            "puddingpulver",
            "agavendicksaft",
            "sirup",
            "streusel",
            "kuchenglasur",
            "biskuit",
            "loeffelbiskuit",
        )
        val SAVORY = listOf(
            "zwiebel",
            "knoblauch",
            "bouillon",
            "bruehe",
            "senf",
            "kaese",
            "parmesan",
            "gruyere",
            "sbrinz",
            "mozzarella",
            "feta",
            "petersilie",
            "basilikum",
            "thymian",
            "rosmarin",
            "schnittlauch",
            "oregano",
            "sojasauce",
            "tomatenmark",
            "essig",
            "olivenoel",
            "kartoffel",
            "linsen",
            "kichererbsen",
            "pfeffer",
            "tofu",
            "kapern",
            "oliven",
            "curry",
            "paprikapulver",
            "majoran",
            "salbei",
            "dill",
        )
        val STEAMER = listOf(
            "dampfgarer",
            "steamer",
            "dampfgarofen",
            "dampfbackofen",
            "combi steam",
            "combisteam",
            "steamgarer",
            "dampfgargeraet",
            "im dampf garen",
            "im dampfgarer",
        )
        val OVEN = listOf(
            "backofen",
            "ofen",
            "backblech",
            "ueberbacken",
            "gratinier",
            "backen",
            "backform",
            "auflaufform",
            "oberhitze",
            "umluft",
            "vorheizen",
        )
        val STOVE = listOf("pfanne", "topf", "anbraten", "koecheln", "herd", "kochen", "sieden", "aufkochen")
    }
}

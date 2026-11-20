package ch.rezeptli.app.domain.nutrition

import ch.rezeptli.app.domain.model.IngredientCategory
import ch.rezeptli.app.domain.model.IngredientNameNormalizer

/**
 * Ein Lebensmittel der Naehrwerttabelle.
 *
 * Kalorien und Eiweiss gelten je 100 g - so, wie man es einkauft: Teigwaren und Reis trocken,
 * Fleisch und Gemuese roh. Die Gewichte dahinter uebersetzen, was Rezepte statt Gramm
 * schreiben: "2 Eier", "1 EL Öl", "1 Bund Petersilie", "1 Dose Pelati".
 */
internal class Food(
    val keys: List<FoodKey>,
    val kcal: Double,
    val protein: Double,
    /** Gramm je Stueck - auch je Becher oder Kugel, wenn das die uebliche Angabe ist. */
    val piece: Double? = null,
    /** Gramm je Milliliter. Mehl ist leichter als Wasser, Honig schwerer. */
    val density: Double = 1.0,
    /** Gramm je Essloeffel; ohne Angabe 15 ml nach [density]. */
    val spoon: Double? = null,
    val bunch: Double? = null,
    /** Gramm je Dose - bei Huelsenfruechten und Mais das Abtropfgewicht. */
    val can: Double? = null,
    val pack: Double? = null,
    val slice: Double? = null,
    /** Kalorien und Eiweiss je 100 g, wenn es gekocht oder aus der Dose kommt. */
    val cooked: Nutrients? = null,
    /** Steht nichts dabei, ist die gekochte Ware gemeint - Kichererbsen kommen meist aus der Dose. */
    val cookedByDefault: Boolean = false,
    /** Traegt nichts bei: Wasser, Salz, Gewuerze. Zaehlt aber auch nicht als "nicht erkannt". */
    val trace: Boolean = false,
    /** Fett zum Braten: Ohne Mengenangabe zaehlt ein Essloeffel - der steht selten da und ist nie nichts. */
    val fat: Boolean = false,
)

/**
 * Ein Stichwort, an dem ein Lebensmittel im Zutatennamen erkannt wird.
 *
 * Deutsche Zutaten sind Zusammensetzungen, und nicht jede meint, was in ihr steckt: In
 * "Schweinefleisch" steckt "wein", in "mehligkochend" steckt "mehl". Darum sagt jedes
 * Stichwort, wie es passen muss:
 *
 * - `=wort` nur als ganzes Wort ("=ei" passt nicht auf "Reis"),
 * - `wort*` am Wortanfang ("lamm*" passt auf "Lammkotelett", nicht auf "Flammkuchen"),
 * - `*wort` am Wortende ("*mehl" passt auf "Weissmehl", nicht auf "mehligkochend"),
 * - sonst irgendwo im Wort - kurze Stichwoerter (bis drei Buchstaben) nur als ganzes Wort.
 *
 * Mehrere Woerter muessen alle vorkommen ("grüne bohnen").
 */
internal class FoodKey(spec: String) {
    private val parts: List<Part> = spec
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .map(::Part)

    fun matches(words: List<String>): Boolean = parts.all { part -> words.any(part::matches) }

    private class Part(spec: String) {
        private val mode: Mode = when {
            spec.startsWith("=") -> Mode.WHOLE
            spec.endsWith("*") -> Mode.START
            spec.startsWith("*") -> Mode.END
            else -> Mode.ANYWHERE
        }
        private val text: String = IngredientNameNormalizer.normalize(spec.trim('=', '*')).replace(" ", "")

        fun matches(word: String): Boolean = when (mode) {
            Mode.WHOLE -> word == text
            Mode.START -> word.startsWith(text)
            Mode.END -> word.endsWith(text)
            Mode.ANYWHERE -> if (text.length <= SHORT) word == text else word.contains(text)
        }
    }

    private enum class Mode { WHOLE, START, END, ANYWHERE }

    private companion object {
        const val SHORT = 3
    }
}

/**
 * Die Naehrwerttabelle: rund zweihundert Lebensmittel, wie sie in Rezepten aus der Schweiz
 * und Deutschland vorkommen.
 *
 * Die Werte sind gerundete Richtwerte ueblicher Naehrwerttabellen, keine Laborwerte - genug,
 * um eine Portion auf einige Dutzend Kalorien genau zu schaetzen. Die Oberflaeche zeigt sie
 * deshalb mit "ca." und sagt dazu, dass geschaetzt wurde.
 *
 * Die Reihenfolge entscheidet: Das erste passende Lebensmittel gilt. Darum steht das
 * Genauere vor dem Allgemeinen ("Kokosmilch" vor "Milch", "Erdnussbutter" vor "Butter") und
 * alles, was einen irrefuehrenden Namen traegt, ganz vorn.
 */
internal object FoodTable {
    fun find(words: List<String>): Food? = FOODS.firstOrNull { food -> food.keys.any { it.matches(words) } }

    /** Wofuer es keinen Eintrag gibt: ein mittlerer Wert der Warengruppe - besser als weglassen. */
    fun typicalOf(category: IngredientCategory): Food? = TYPICAL[category]

    private val TYPICAL: Map<IngredientCategory, Food> = mapOf(
        IngredientCategory.GEMUESE to typical(30, 2.0, piece = 100, bunch = 150),
        IngredientCategory.FRUECHTE to typical(55, 0.7, piece = 120),
        IngredientCategory.MILCHPRODUKTE to typical(160, 9.0, piece = 200),
        IngredientCategory.FLEISCH_FISCH to typical(170, 20.0, piece = 150),
        IngredientCategory.TEIGWAREN_GETREIDE to typical(340, 11.0),
        IngredientCategory.KONSERVEN_SAUCEN to typical(80, 3.0, can = 400),
        IngredientCategory.BACKEN_SUESSES to typical(400, 5.0),
        IngredientCategory.TIEFKUEHL to typical(90, 4.0),
        IngredientCategory.GETRAENKE to typical(40, 0.3),
        IngredientCategory.GEWUERZE_KRAEUTER to Food(keys = emptyList(), kcal = 0.0, protein = 0.0, trace = true),
    )

    private fun typical(kcal: Int, protein: Double, piece: Int? = null, bunch: Int? = null, can: Int? = null) = Food(
        keys = emptyList(),
        kcal = kcal.toDouble(),
        protein = protein,
        piece = piece?.toDouble(),
        bunch = bunch?.toDouble(),
        can = can?.toDouble(),
    )

    private val FOODS: List<Food> = buildList {
        fun food(
            keys: String,
            kcal: Number,
            protein: Number,
            piece: Number? = null,
            density: Double = 1.0,
            spoon: Number? = null,
            bunch: Number? = null,
            can: Number? = null,
            pack: Number? = null,
            slice: Number? = null,
            cooked: Pair<Number, Number>? = null,
            cookedByDefault: Boolean = false,
            fat: Boolean = false,
        ) {
            add(
                Food(
                    keys = keys.split(',').map(::FoodKey),
                    kcal = kcal.toDouble(),
                    protein = protein.toDouble(),
                    piece = piece?.toDouble(),
                    density = density,
                    spoon = spoon?.toDouble(),
                    bunch = bunch?.toDouble(),
                    can = can?.toDouble(),
                    pack = pack?.toDouble(),
                    slice = slice?.toDouble(),
                    cooked = cooked?.let { Nutrients(it.first.toDouble(), it.second.toDouble()) },
                    cookedByDefault = cookedByDefault,
                    fat = fat,
                ),
            )
        }

        fun trace(keys: String) {
            add(Food(keys = keys.split(',').map(::FoodKey), kcal = 0.0, protein = 0.0, trace = true))
        }

        // ---------------------------------------------------------------- Namen, die taeuschen
        food("wassermelone, honigmelone, melone", 35, 0.6, piece = 1500, slice = 150)
        food("kirschwasser, zwetschgenwasser, =kirsch, obstbrand", 230, 0)
        // "Eis" ohne Zusatz ist das Eis im Glas - Glace heisst "Vanilleeis", "Glace" oder "Eiscreme".
        trace("wasser, eiswürfel, crushed ice, =eis")
        trace("muskatnuss, =muskat, bohnenkraut, zitronengras, zitronensäure")
        food("nüsslisalat, nüssler, feldsalat", 20, 2, pack = 100)
        food("essiggurke, cornichon, essigzwiebel, gewürzgurke, salzgurke", 15, 0.5, piece = 20)
        food("kräuterseitling", 25, 3, piece = 60)
        food("rahmspinat", 80, 3, pack = 500, piece = 500)
        food("proteinpulver, eiweisspulver, =whey, molkenprotein", 380, 75, spoon = 10)
        food("zuckerschoten, kefen, zuckererbsen", 40, 3)
        food("zuckermais", 85, 3, can = 150)
        food("weintrauben, trauben", 70, 0.7)
        food("preiselbeer*", 45, 0.3, spoon = 20)
        food("granatapfel", 80, 1.5, piece = 150)
        food("butternuss, butternut", 40, 1, piece = 1000)
        food("fleischtomate", 20, 1, piece = 200)
        food(
            "erdnussbutter, erdnussmus, mandelmus, nussmus, cashewmus, tahini, =tahin, sesampaste",
            610,
            22,
            spoon = 16,
        )
        food("nussnougat, nutella, haselnusscreme, schokoaufstrich", 540, 6, spoon = 20)
        food("fleischkäse, leberkäse", 290, 12, slice = 100, piece = 100)
        food("mandelmehl, gemahlene mandeln, gemahlene haselnüsse, gemahlene nüsse", 620, 20, spoon = 8, density = 0.45)
        food("milchreis, risottoreis, rundkornreis", 350, 7, density = 0.85, cooked = 130 to 2.7)
        food("reispapier", 340, 6, piece = 8)
        food("reiswein, mirin, =sake", 130, 0.3)
        food(
            "schokolade, schoggi, kuvertüre, couverture, schokodrops, schokotropfen, schokowürfel",
            540,
            6.5,
            piece = 100,
            spoon = 10,
        )
        food(
            "kekse, =keks, guetzli, biskuit, löffelbiskuit, amaretti, cantucci, butterkeks, lebkuchen",
            450,
            6,
            piece = 8,
        )
        food("=zopf, butterzopf", 320, 9, slice = 40, piece = 500)

        // ---------------------------------------------------------------- Bruehen, Saucen, Wuerziges
        food("bouillon, brühe, =fond, *fond, suppenwürfel, brühwürfel", 5, 0.4, piece = 10)
        food("essig, balsamico, aceto", 40, 0.3)
        food("sojasauce, sojasosse, tamari, shoyu", 60, 8, spoon = 16)
        food("fischsauce, fischsosse, austernsauce, austernsosse", 60, 5, spoon = 16)
        food("chilisauce, chilisosse, chili sauce, sriracha, hoisin, teriyaki, bbq, barbecue", 180, 1.5, spoon = 18)
        food("worcester*", 80, 1)
        trace("tabasco")
        food("ketchup", 100, 1.5, spoon = 17)
        food("mayonnaise, =mayo, aioli, remoulade, tartarsauce", 650, 1, spoon = 14)
        food("hollandaise, bearnaise", 350, 2)
        food("bechamel", 130, 4)
        food("bratensauce, bratensosse, bratenjus, =jus", 60, 2)
        food("salatsauce, salatsosse, dressing, vinaigrette", 300, 1, spoon = 12)
        food("senf", 150, 6, spoon = 15)
        food("pesto", 450, 5, spoon = 15, piece = 190)
        food("currypaste, curry paste, misopaste, =miso, harissa, sambal", 130, 4, spoon = 15)
        food("tomatenmark, tomatenpüree, tomatenkonzentrat", 80, 4.5, spoon = 16, can = 140)
        food(
            "passata, tomatensauce, tomatensosse, pastasauce, sugo, passierte tomaten",
            35,
            1.5,
            can = 400,
            pack = 500,
            piece = 500,
        )
        food(
            "pelati, dosentomate, gehackte tomaten, geschälte tomaten, stückige tomaten, polpa",
            25,
            1.2,
            can = 400,
            pack = 400,
        )
        food("getrocknete tomaten, dörrtomate", 200, 9, piece = 8)
        food("cherrytomate, cocktailtomate, kirschtomate, datteltomate", 20, 1, piece = 15)
        food("kokosmilch, kokoscreme, kokosnussmilch", 190, 2, can = 400, pack = 250)
        food("kokosraspel, kokosflocken, kokosette", 650, 6, spoon = 6)
        food("kokosöl, kokosfett", 880, 0, density = 0.92, spoon = 12, fat = true)

        // ---------------------------------------------------------------- Milch, Kaese, Eier
        food("sojamilch, sojadrink, haferdrink, hafermilch, mandelmilch, mandeldrink, reisdrink, reismilch", 45, 1.5)
        food("sojarahm, hafercuisine, sojacuisine, pflanzenrahm", 170, 1.5)
        food("kondensmilch", 250, 7)
        food("buttermilch, =kefir", 45, 3.4)
        food("quark, topfen", 90, 11, piece = 250, spoon = 20)
        food("frischkäse, philadelphia", 250, 5.5, piece = 200, spoon = 20)
        food("mascarpone", 400, 4.5, piece = 250, spoon = 20)
        food("ricotta", 160, 9, piece = 250, spoon = 20)
        food("hüttenkäse, cottage", 100, 12, piece = 200)
        food("mozzarella, burrata", 250, 18, piece = 150)
        food("feta, schafskäse, hirtenkäse", 265, 16, piece = 200, pack = 200)
        food("halloumi, grillkäse", 320, 22, piece = 250)
        food("parmesan, sbrinz, grana, pecorino", 400, 34, spoon = 8)
        food("gorgonzola, blauschimmel, roquefort", 350, 20)
        food("ziegenkäse, camembert, =brie, tomme", 300, 19, piece = 150)
        food("raclette*", 350, 23, slice = 30)
        food(
            "käse, gruyere, greyerzer, emmentaler, appenzeller, tilsiter, cheddar, gouda, edamer, vacherin",
            380,
            26,
            slice = 20,
            spoon = 8,
        )
        food("sauerrahm, saure sahne, schmand, creme fraiche, crème fraîche", 250, 2.5, piece = 200, spoon = 20)
        food("halbrahm, kaffeerahm, saucenrahm, kochrahm, cremefine", 220, 2.5, piece = 250, spoon = 15)
        food("rahm, sahne, =obers, schlagobers", 340, 2.1, piece = 250, spoon = 15, pack = 250)
        food("milch", 65, 3.3, density = 1.03)
        food("joghurt, jogurt, yoghurt, yogurt, =skyr", 70, 4.5, piece = 180, spoon = 18)
        food("eigelb, eidotter, =dotter", 350, 16, piece = 18)
        food("eiweiss, eiklar", 50, 11, piece = 33)
        food("=ei, =eier, *eier, =eiern", 150, 12.5, piece = 55)

        // ---------------------------------------------------------------- Fette
        food("butter, margarine, schmalz, =ghee, bratfett, =fett", 750, 0.6, spoon = 12, piece = 20, fat = true)
        food("=öl, *öl", 880, 0, density = 0.92, spoon = 12, fat = true)

        // ---------------------------------------------------------------- Fleisch, Fisch und was sie ersetzt
        food("sojaschnetzel, sojahack, sojagranulat", 330, 50)
        food("hackfleisch, =gehacktes, =ghackets, =ghackts, *hack, hackbällchen", 230, 19)
        food("speck, bacon, pancetta, guanciale", 330, 15, slice = 15, pack = 120)
        food(
            "rohschinken, parmaschinken, serrano, prosciutto, bresaola, bündnerfleisch, trockenfleisch",
            230,
            27,
            slice = 12,
        )
        food("schinken", 115, 19, slice = 20, pack = 120)
        food("salami, chorizo, landjäger, salsiz", 400, 21, slice = 5, piece = 80)
        food(
            "wurst, würst, wienerli, cervelat, cipollata, =lyoner, frankfurter, merguez",
            270,
            13,
            piece = 100,
            slice = 10,
        )
        // Das ganze Tier: Als Stueck gezaehlt ist es kein Brustfilet.
        food("=poulet, =hähnchen, =huhn, suppenhuhn, brathähnchen, =güggeli, poularde", 190, 19, piece = 700)
        food("poulet, huhn, hähnchen, hühn*, geflügel, truthahn, =pute, puten*, trute*", 130, 22, piece = 160)
        food("=ente, enten*", 220, 18, piece = 180)
        food("lamm*", 200, 19, piece = 100)
        food("kalb*", 130, 21, piece = 150)
        food("schwein*", 160, 20, piece = 150)
        food("rind*, entrecote, =steak, =steaks, =huft, siedfleisch, roastbeef", 160, 21, piece = 180)
        food(
            "geschnetzeltes, schnitzel, plätzli, =braten, *braten, kotelett, =ragout, gulasch, =fleisch, " +
                "suppenfleisch, grillfleisch, fleischwürfel, fleischstreifen",
            150,
            21,
            piece = 150,
        )
        // Vor dem Lachs: Im "Seelachs" steckt er nur dem Namen nach.
        food("seelachs, kabeljau, dorsch, pangasius, zander, =egli, felchen, forelle", 100, 19, piece = 150)
        food("lachs", 200, 20, piece = 150, slice = 25)
        food("thunfisch, =thon", 130, 24, can = 110, piece = 150)
        food("sardelle, anchovis, sardine", 200, 22, piece = 4, can = 50)
        food("crevette, garnele, shrimp, scampi, gamba", 85, 18, piece = 15)
        food("muschel, calamari, tintenfisch, =pulpo", 80, 15)
        food("fischstäbchen", 200, 12, piece = 30)
        food("=fisch, *fisch, fischfilet", 100, 19, piece = 150)
        food("räuchertofu", 160, 17, piece = 200, pack = 200)
        food("tofu, tempeh, seitan, quorn", 135, 15, piece = 200, pack = 200)

        // ---------------------------------------------------------------- Teigwaren, Getreide, Brot, Kartoffeln
        food("spätzli, spätzle, knöpfli, gnocchi, schupfnudeln", 170, 5, pack = 500)
        food("tortellini, tortelloni, ravioli, maultaschen", 280, 11, pack = 250)
        food("lasagneblätter, lasagneplatten, lasagneblatt, =lasagne", 355, 12.5, piece = 20, pack = 250)
        food(
            "teigwaren, pasta, spaghetti, penne, nudel, hörnli, hörndli, fusilli, tagliatelle, makkaroni, magronen, " +
                "maccheroni, farfalle, rigatoni, linguine, orecchiette, =orzo, risoni, fideli, ramen, =soba",
            355,
            12.5,
            pack = 500,
            cooked = 140 to 5,
        )
        food(
            "couscous, bulgur, quinoa, hirse, =ebly, gerste, graupen, buchweizen, amaranth",
            350,
            12,
            density = 0.85,
            cooked =
                120 to 4,
        )
        food("polenta, maisgriess, griess", 350, 9, density = 0.7, spoon = 12)
        food("reis", 350, 7, density = 0.85, cooked = 130 to 2.7)
        food(
            "haferflocken, dinkelflocken, getreideflocken, hirseflocken, =hafer, müesli, müsli, granola, cornflakes, kleie",
            370,
            12,
            density = 0.4,
            spoon = 8,
        )
        food("paniermehl, semmelbrösel, panko, brotbrösel, brosamen", 360, 12, density = 0.45, spoon = 8)
        food(
            "stärke, maizena, mondamin, kartoffelmehl, saucenbinder, puddingpulver",
            350,
            0.5,
            density = 0.55,
            spoon = 8,
            pack = 40,
        )
        food("=mehl, *mehl", 340, 11, density = 0.6, spoon = 8)
        food("toast*", 260, 8, slice = 25, piece = 25)
        food("brötchen, semmel, bürli, weggli, =bun, =buns, brioche, =bagel, laugen*", 280, 9, piece = 60)
        food("baguette, ciabatta, fladenbrot, =pita, =naan, focaccia", 270, 9, piece = 250, slice = 30)
        food("tortilla, =wrap, =wraps, =taco, =tacos", 300, 8, piece = 60)
        food("knäckebrot, zwieback, cracker, crostini, croutons, croûtons", 380, 10, slice = 10, piece = 10)
        food("brot", 250, 8.5, slice = 40, piece = 400)
        food("süsskartoffel, batate", 86, 1.6, piece = 300)
        food("kartoffelchips, =chips", 540, 6)
        food("kartoffelstock, kartoffelpüree", 90, 2)
        food("kartoffel, härdöpfel, gschwellti, erdäpfel", 77, 2, piece = 110)
        food("blätterteig", 380, 5, pack = 300, piece = 300)
        food("=kuchenteig, mürbeteig, mürbteig, quicheteig, wähenteig", 400, 6, pack = 300, piece = 300)
        food("pizzateig", 250, 8, pack = 450, piece = 450)
        food("strudelteig, filoteig, yufka, fillo*, brickteig, wontonblätter", 290, 8, pack = 120, piece = 15)
        food("teig", 300, 8, pack = 400, piece = 400)

        // ---------------------------------------------------------------- Suesses
        food("puderzucker, staubzucker", 400, 0, density = 0.55, spoon = 8)
        food("vanillezucker, vanillinzucker", 400, 0, pack = 8, spoon = 12, piece = 8)
        food(
            "honig, ahornsirup, agavendicksaft, agavensirup, melasse, dicksaft, sirup, =birnel",
            310,
            0.3,
            density = 1.35,
            spoon = 20,
        )
        food("zucker", 400, 0, density = 0.85, spoon = 13)
        food("konfitüre, marmelade, gelee, =gonfi", 250, 0.4, spoon = 20)
        food("kakao", 350, 20, density = 0.5, spoon = 6)
        food("marzipan", 460, 6)
        food("vanilleeis, vanilleglace, glace, eiscreme, speiseeis", 200, 3.5, piece = 60)
        food("meringue, baiser", 390, 5, piece = 10)

        // ---------------------------------------------------------------- Nuesse, Kerne, Getrocknetes
        food("mandel", 600, 21, spoon = 8, piece = 1.2)
        food("haselnuss, haselnüsse", 650, 14, spoon = 8, piece = 1.2)
        food("baumnuss, baumnüsse, walnuss, walnüsse, pekan*", 680, 15, spoon = 8, piece = 5)
        food("cashew", 580, 18, spoon = 10)
        food("erdnuss, erdnüsse", 590, 25, spoon = 10)
        food("pinienkerne, =pinien", 680, 14, spoon = 10)
        food("pistazie", 580, 20, spoon = 10)
        food("sesam", 590, 18, spoon = 9)
        food(
            "sonnenblumenkerne, kürbiskerne, leinsamen, chiasamen, =chia, hanfsamen, mohn, =samen, =kerne",
            560,
            22,
            spoon = 10,
        )
        food("marroni, maroni, kastanie", 200, 3)
        food(
            "rosinen, sultaninen, weinbeeren, korinthen, cranberr*, datteln, dörr*, trockenfrüchte, getrocknete aprikosen",
            290,
            2.5,
            spoon = 10,
            piece = 8,
        )
        food("nuss, nüsse", 620, 17, spoon = 8)

        // ---------------------------------------------------------------- Huelsenfruechte, Mais
        food("linsen, =dal", 330, 24, cooked = 110 to 8, can = 240)
        food("kichererbsen", 350, 19, cooked = 120 to 7, can = 240, cookedByDefault = true)
        food(
            "kidneybohnen, =kidney, weisse bohnen, schwarze bohnen, rote bohnen, dicke bohnen, borlotti, cannellini, " +
                "indianerbohnen, bohnenkerne",
            310,
            21,
            cooked = 100 to 7,
            can = 250,
            cookedByDefault = true,
        )
        food("edamame, sojabohnen", 120, 11)
        food("erbsen, erbsli", 80, 6, can = 140)
        food("grüne bohnen, buschbohnen, stangenbohnen, =bohnen, =böhnli", 30, 2, cooked = 100 to 7, can = 250)
        food("mais, maiskörner", 85, 3, can = 150, piece = 200)

        // ---------------------------------------------------------------- Getraenke
        food("zitronensaft, limettensaft", 25, 0.4, piece = 40)
        food("apfelmus, apfelkompott, kompott, fruchtmus", 70, 0.3)
        food("=saft, *saft, =most, süssmost", 48, 0.4, piece = 50)
        food(
            "weisswein, rotwein, kochwein, =wein, portwein, marsala, sherry, prosecco, =sekt, champagner, wermut",
            75,
            0.1,
        )
        food("=bier, lagerbier, weissbier", 40, 0.4, piece = 330, can = 330)
        food(
            "=rum, wodka, vodka, =gin, whisky, whiskey, tequila, cognac, brandy, grappa, schnaps, cacha*, =pisco",
            230,
            0,
        )
        food("likör, amaretto, baileys, cointreau, aperol, campari, triple sec, kahlua, limoncello", 270, 0.3)
        food("=cola, limonade, tonic, ginger ale, ginger beer, eistee, bitter lemon", 40, 0)
        trace("kaffee, espresso, =tee, schwarztee, grüntee, kräutertee, =soda")

        // ---------------------------------------------------------------- Gewuerze und Kraeuter
        trace(
            "=salz, meersalz, kräutersalz, salzflocken, fleur sel, =pfeffer, pfefferkörner, cayenne*, zitronenpfeffer",
        )
        trace("paprikapulver, rosenpaprika, edelsüss, rosenscharf, =curry, currypulver, kümmel, cumin, kurkuma")
        trace("zimt, nelke, kardamom, sternanis, =anis, fenchelsamen, koriandersamen, wacholder, piment, safran")
        trace("chiliflocken, chilipulver, gewürz*, würze, =aromat, streuwürze, masala, hanout, sumach, zaatar")
        trace("backpulver, natron, weinstein, gelatine, =agar, hefe, vanille, aroma, bittermandel*, lebensmittelfarbe")
        trace("zitronenschale, orangenschale, limettenschale, =schale, abrieb, zesten, kaffirlimetten*, lorbeer")
        food(
            "petersilie, peterli, basilikum, schnittlauch, koriander, dill, minz*, thymian, rosmarin, salbei, " +
                "oregano, majoran, estragon, kerbel, liebstöckel, bärlauch, kräuter, melisse, kresse",
            40,
            3,
            bunch = 30,
            piece = 2,
            spoon = 3,
        )
        food("ingwer", 80, 1.8, piece = 20, spoon = 6)

        // ---------------------------------------------------------------- Gemuese
        food("frühlingszwiebel, bundzwiebel, lauchzwiebel", 30, 1.8, piece = 15, bunch = 120)
        food("schalotte", 70, 2.5, piece = 25)
        food("zwiebel", 40, 1.2, piece = 100)
        food("knoblauch, knobli", 140, 6, piece = 4)
        food("lauch, porree", 30, 1.5, piece = 150)
        food("karotte, rüebli, möhre", 40, 1, piece = 80, bunch = 500)
        food("tomate", 20, 1, piece = 120)
        food("chili, peperoncin*, jalape*, chilischote", 40, 2, piece = 10)
        food("paprika, peperoni", 30, 1, piece = 160)
        food("zucchini, zucchetti", 20, 1.5, piece = 250)
        food("aubergine", 25, 1, piece = 300)
        food("gurke, cornichon", 14, 0.7, piece = 400)
        food("spinat", 25, 2.8, pack = 500)
        food("mangold, krautstiel, =pak, =choi, pakchoi", 20, 2, piece = 300)
        food(
            "rucola, kopfsalat, eisberg*, lattich, salat, chicoree, radicchio, endivie",
            20,
            1.5,
            piece = 300,
            pack = 100,
            bunch = 100,
        )
        food("brokkoli, broccoli", 35, 3, piece = 400)
        food("blumenkohl, romanesco", 25, 2, piece = 800)
        food("rosenkohl", 45, 4)
        food("kohlrabi", 30, 2, piece = 250)
        food("kohl, kabis, wirz, wirsing, federkohl, sauerkraut", 30, 1.5, piece = 1000)
        food("fenchel", 25, 1.5, piece = 250)
        food("stangensellerie, staudensellerie, bleichsellerie", 15, 1, piece = 40)
        food("sellerie", 30, 1.5, piece = 500)
        food("kürbis", 30, 1, piece = 1200)
        food("champignon, pilz, eierschwämm*, eierschwamm, pfifferling, shiitake, kräuterseitling", 25, 3, piece = 20)
        food("randen, =rande, rote bete, rote beete", 45, 1.6, piece = 150)
        food("spargel", 20, 2, bunch = 500, piece = 40)
        food("radieschen, rettich", 15, 1, bunch = 150, piece = 10)
        food("pastinake, petersilienwurzel, topinambur, schwarzwurzel", 60, 1.5, piece = 120)
        food("avocado", 160, 2, piece = 150)
        food("olive", 150, 1, piece = 4, spoon = 10)
        food("kapern", 25, 2, spoon = 8)
        food("sprossen, sojasprossen", 30, 3)
        food("artischocke", 50, 2.5, piece = 120, can = 240)

        // ---------------------------------------------------------------- Fruechte
        food("zitrone, limette, limone", 30, 0.7, piece = 60)
        food("orange, mandarine, clementine, grapefruit", 45, 0.9, piece = 160)
        food("apfel, äpfel", 55, 0.3, piece = 150)
        food("birne", 55, 0.5, piece = 160)
        food("banane", 90, 1, piece = 120)
        food("beere, =beeren", 40, 1)
        food("mango", 60, 0.8, piece = 300)
        food("ananas", 55, 0.5, piece = 900, slice = 80, can = 340)
        food("pfirsich, nektarine", 40, 0.9, piece = 130)
        food("aprikose, zwetschge, pflaume, mirabelle", 45, 0.8, piece = 40)
        food("kirsche", 60, 1)
        food("kiwi", 60, 1, piece = 80)
        food("feige", 70, 1, piece = 50)
        food("rhabarber", 20, 0.6, piece = 60, bunch = 500)

        // ---------------------------------------------------------------- wenn sonst nichts passt
        food("gemüse", 30, 2)
        food("obst, früchte, =frucht", 55, 0.7)
    }
}

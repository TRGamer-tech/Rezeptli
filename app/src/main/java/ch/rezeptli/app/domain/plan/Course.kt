package ch.rezeptli.app.domain.plan

/** Was ein Rezept ist - ein Essen fuer den Tisch, ein Getraenk, etwas Suesses oder Beiwerk. */
enum class Course {
    HAUPTGERICHT,
    GETRAENK,

    /** Desserts und suesses Gebaeck. */
    DESSERT,

    /** Saucen, Dips, Brot, Fruehstueck, Eingemachtes - Essen, aber keine Mahlzeit. */
    SONSTIGES,
}

/** Wonach beim Wischen gesucht wird. */
enum class CourseChoice {
    ESSEN,
    GETRAENKE,
    BEIDES,
    ;

    fun allows(course: Course): Boolean = when (this) {
        ESSEN -> course != Course.GETRAENK
        GETRAENKE -> course == Course.GETRAENK
        BEIDES -> true
    }

    companion object {
        fun fromName(value: String?): CourseChoice? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Liest aus Titel, Schlagworten und Zutaten, was ein Rezept ist.
 *
 * Der Titel zaehlt am meisten, weil er oft das Einzige ist, was die App kennt: Die
 * Verzeichnisse der Quellen nennen keine Zutaten. Deutsche Rezepttitel nennen das Gericht
 * im Grundwort ("Erdbeer-Smoothie", "Apfelkuchen") oder vor dem Verhaeltniswort ("Lachs mit
 * Tee-Marinade" ist ein Lachs, kein Tee) - darum wird nur der Teil vor "mit", "an", "in"
 * gelesen und am Wortende verglichen.
 *
 * Das ist eine Einschaetzung. Im Zweifel bleibt ein Rezept ein Hauptgericht: Lieber
 * einmal einen Kuchen zu viel zeigen als ein Abendessen verstecken.
 */
internal object CourseRules {
    /**
     * [title] sind die Woerter des Titels in ihrer Reihenfolge, [tags] die der Schlagworte.
     * [sweet] und [savory] zaehlen suesse und herzhafte Zutaten; ohne Zutaten sind beide 0.
     */
    fun of(title: List<String>, tags: Set<String>, sweet: Int, savory: Int): Course {
        val head = headOf(title)

        if (isDrinkTitle(head)) return Course.GETRAENK
        if (tags.any { it in DRINK_TAGS } && head.none(::isFoodAnchor)) return Course.GETRAENK
        if (tags.any { it in MAIN_TAGS }) return Course.HAUPTGERICHT

        if (isDessertTitle(head)) return Course.DESSERT
        if (isSideTitle(head)) return Course.SONSTIGES

        if (tags.any { it in DESSERT_TAGS }) return Course.DESSERT
        if (tags.any { it in SIDE_TAGS }) return Course.SONSTIGES

        if (sweet >= SWEET_THRESHOLD && savory == 0) return Course.DESSERT
        return Course.HAUPTGERICHT
    }

    /** Der Teil des Titels, der das Gericht nennt: alles vor dem ersten Verhaeltniswort. */
    private fun headOf(title: List<String>): List<String> {
        val cut = title.indexOfFirst { it in PREPOSITIONS }
        return if (cut <= 0) title else title.subList(0, cut)
    }

    private fun isDrinkTitle(head: List<String>): Boolean {
        if (head.any(::isFoodAnchor)) return false
        if (head.any { word -> DRINK_ENDINGS.any { word.endsWith(it) } && word !in NOT_A_DRINK }) return true
        if (head.any { it in DRINK_WORDS }) return true
        val last = head.lastOrNull().orEmpty()
        // "Mandelmilch", "Bananenmilch": ein kurzer Titel, der auf Milch endet, ist zum Trinken.
        if (head.size <= 2 && last.endsWith("milch") && last !in NOT_A_DRINK) return true
        // "Heisse Schokolade": erst das Beiwort macht ein Getraenk daraus.
        val warm = head.any { it.startsWith("heiss") || it.startsWith("trink") }
        return warm && last.endsWith("schokolade")
    }

    private fun isDessertTitle(head: List<String>): Boolean {
        val savoryWord = head.any { word -> SAVORY_PARTS.any { word.contains(it) } }
        if (head.any { word -> DESSERT_ENDINGS.any { word.endsWith(it) } } && !savoryWord) return true
        if (head.any { it in DESSERT_WORDS }) return true
        if (head.any(::isIceCream)) return true
        if ("panna" in head && "cotta" in head) return true
        // Waehe, Strudel und Tarte gibt es herzhaft wie suess - die Frucht entscheidet.
        val fruity = head.any { word -> FRUITS.any { word.contains(it) } }
        return fruity && head.any { word -> EITHER_WAY.any { word.endsWith(it) } }
    }

    /**
     * "Eis" und "Vanilleeis" - aber weder Reis noch Preis.
     *
     * Die Falle dabei: "Erdbeereis" und "Wassereis" enden auf "reis" und sind trotzdem Eis.
     * Darum zaehlen die Wortstaemme, die auf "r" enden und vor "eis" stehen, zuerst.
     */
    private fun isIceCream(word: String): Boolean = when {
        word == EIS -> true
        !word.endsWith(EIS) || word.length <= EIS.length + 2 -> false
        ICE_STEMS.any { word.endsWith(it + EIS) } -> true
        else -> NOT_ICE.none { word.endsWith(it) }
    }

    private fun isSideTitle(head: List<String>): Boolean {
        if (head.isEmpty()) return false
        if (head.any { it in MEAL_ANCHORS || MEAL_ANCHORS.any { anchor -> it.endsWith(anchor) } }) return false
        val last = head.last()
        return SIDE_ENDINGS.any { last.endsWith(it) && last !in NOT_A_SIDE } || head.first() in SIDE_WORDS
    }

    private fun isFoodAnchor(word: String): Boolean = FOOD_ANCHORS.any { word.endsWith(it) }

    private const val SWEET_THRESHOLD = 2
    private const val EIS = "eis"

    private val PREPOSITIONS = setOf(
        "mit",
        "an",
        "auf",
        "in",
        "im",
        "zu",
        "zum",
        "zur",
        "vom",
        "von",
        "aus",
        "unter",
        "ueber",
        "nach",
        "alla",
        "con",
    )

    /** Endet ein Wort darauf, ist es ein Getraenk: "Erdbeersmoothie", "Apfelsaft", "Kraeutertee". */
    private val DRINK_ENDINGS = listOf(
        "smoothie",
        "smoothies",
        "shake",
        "shakes",
        "cocktail",
        "cocktails",
        "mocktail",
        "mocktails",
        "drink",
        "drinks",
        "limonade",
        "limonaden",
        "limo",
        "sirup",
        "punsch",
        "bowle",
        "gluehwein",
        "tee",
        "kaffee",
        "cappuccino",
        "frappe",
        "lassi",
        "spritz",
        "mojito",
        "sangria",
        "caipirinha",
        "daiquiri",
        "negroni",
        "colada",
        "margarita",
        "likoer",
        "schnaps",
        "grog",
        "eggnog",
        "saft",
        "saefte",
        "schorle",
        "trinkschokolade",
        "eisschokolade",
        "spritzer",
        "limoncello",
        "kombucha",
        "wasser",
    )

    /** Ganze Woerter, die fuer sich ein Getraenk sind. */
    private val DRINK_WORDS = setOf(
        "latte",
        "macchiato",
        "espresso",
        "hugo",
        "aperol",
        "gin",
        "tonic",
        "kakao",
        "martini",
        "prosecco",
        "cider",
        "kir",
        "mule",
        "julep",
        "water",
    )

    /** Enden wie ein Getraenk, sind aber keines. */
    private val NOT_A_DRINK = setOf(
        "rosenwasser",
        "orangenbluetenwasser",
        "salzwasser",
        "kochwasser",
        "zuckerwasser",
        "satee",
        "kokosmilch",
        "buttermilch",
        "sauermilch",
        "dickmilch",
        "kondensmilch",
    )

    /** Steht eines davon im Titel, ist es etwas zu essen - "Espresso-Brownies" sind kein Kaffee. */
    private val FOOD_ANCHORS = listOf(
        "kuchen",
        "torte",
        "torten",
        "cake",
        "cakes",
        "muffin",
        "muffins",
        "brownie",
        "brownies",
        "cookie",
        "cookies",
        "guetzli",
        "glace",
        "sorbet",
        "parfait",
        "creme",
        "mousse",
        "pudding",
        "tiramisu",
        "dessert",
        "konfekt",
        "truffes",
        "pralinen",
        "glasur",
        "gelee",
        "konfituere",
        "marmelade",
        "bowl",
        "sauce",
        "sosse",
        "marinade",
        "braten",
        "suppe",
        "risotto",
        "poulet",
        "huhn",
        "lachs",
        "filet",
        "ragout",
        "gulasch",
        "eintopf",
        "brot",
        "zopf",
        "cupcake",
        "cupcakes",
        "schnitten",
        "tarte",
        "waehe",
        "strudel",
        "crumble",
        "muesli",
        "porridge",
        "pancakes",
        "waffeln",
        "dip",
        "dressing",
        "chutney",
        "kekse",
        "plaetzchen",
        "biskuit",
        "panna",
        "cotta",
        "eis",
        "trifle",
        "roulade",
        "fondue",
        "kugeln",
        "riegel",
        "bonbons",
        "zucker",
        "pizza",
        "pasta",
        "salat",
        "burger",
        "sandwich",
        "curry",
        "pfanne",
        "auflauf",
        "gratin",
        "steak",
        "schnitzel",
        "wurst",
        "kaese",
        "reis",
        "nudeln",
        "kartoffeln",
        "gemuese",
        "tofu",
        "fisch",
        "crevetten",
        "ente",
        "lamm",
        "rind",
        "schwein",
        "kalb",
        "wrap",
        "taco",
        "spiess",
        "spiesse",
        "pilze",
        "haeppchen",
    )

    private val DESSERT_ENDINGS = listOf(
        "kuchen",
        "torte",
        "torten",
        "cake",
        "cakes",
        "cheesecake",
        "muffin",
        "muffins",
        "cupcake",
        "cupcakes",
        "brownie",
        "brownies",
        "cookie",
        "cookies",
        "guetzli",
        "guetsli",
        "plaetzchen",
        "keks",
        "kekse",
        "biskuit",
        "biscuits",
        "glace",
        "sorbet",
        "parfait",
        "pudding",
        "tiramisu",
        "dessert",
        "mousse",
        "creme",
        "kompott",
        "konfekt",
        "praline",
        "pralinen",
        "truffes",
        "fudge",
        "crumble",
        "stollen",
        "berliner",
        "donut",
        "donuts",
        "macaron",
        "macarons",
        "meringue",
        "meringues",
        "baiser",
        "waffel",
        "waffeln",
        "flan",
        "koepfli",
        "clafoutis",
        "trifle",
        "zabaione",
        "semifreddo",
        "cantucci",
        "amaretti",
        "florentiner",
        "spitzbuben",
        "mailaenderli",
        "brunsli",
        "laeckerli",
        "chraebeli",
        "kipferl",
        "zimtsterne",
        "zimtschnecken",
        "vermicelles",
        "apfelmus",
        "riegel",
        "bonbons",
        "eclairs",
        "profiteroles",
        "madeleines",
        "churros",
        "baklava",
        "griessbrei",
        "milchreis",
        "roetegruetze",
        "gruetze",
        "schokofruechte",
        "lebkuchen",
        "magenbrot",
        "nussgipfel",
    )

    private val DESSERT_WORDS = setOf("suesses", "nachtisch", "nachspeise", "suessspeise", "eiscreme")

    /** Herzhaftes im Wort hebt "Kuchen" und "Creme" auf: Zwiebelkuchen, Lachsmousse, Avocadocreme. */
    private val SAVORY_PARTS = listOf(
        "zwiebel",
        "lauch",
        "speck",
        "gemuese",
        "spinat",
        "tomate",
        "kartoffel",
        "kaese",
        "chaes",
        "fisch",
        "lachs",
        "forelle",
        "thunfisch",
        "schinken",
        "leber",
        "avocado",
        "randen",
        "erbsen",
        "pfann",
        "flamm",
        "reibe",
        "kuerbis",
        "zucchetti",
        "zucchini",
        "broccoli",
        "brokkoli",
        "pilz",
        "champignon",
        "wurst",
        "fleisch",
        "kraeuter",
        "knoblauch",
        "sauer",
        "oliven",
        "peperoni",
        "paprika",
        "hack",
        "poulet",
        "raclette",
        "spargel",
        "sellerie",
        "fenchel",
        "bohnen",
        "linsen",
        "kohl",
        "kabis",
        "mais",
        "aubergine",
        "mangold",
        "wirz",
        "feta",
        "mozzarella",
        "parmesan",
        "gruyere",
        "tofu",
        "thon",
        "crevetten",
        "garnelen",
    )

    private val NOT_ICE = listOf("reis", "preis", "kreis", "weis")

    /** Vor "eis" machen sie aus dem vermeintlichen Reis ein Eis: Erdbeer-eis, Wasser-eis. */
    private val ICE_STEMS = listOf("beer", "wasser", "ingwer", "holunder", "likoer")

    private val EITHER_WAY =
        listOf("waehe", "strudel", "tarte", "tartelettes", "galette", "roulade", "schnitten", "wickel")

    private val FRUITS = listOf(
        "apfel",
        "aepfel",
        "birne",
        "zwetschge",
        "pflaume",
        "aprikose",
        "marille",
        "rhabarber",
        "kirsch",
        "beere",
        "erdbeer",
        "himbeer",
        "heidelbeer",
        "brombeer",
        "johannisbeer",
        "pfirsich",
        "mango",
        "banane",
        "zitrone",
        "limette",
        "orange",
        "mohn",
        "nuss",
        "mandel",
        "schoko",
        "quark",
        "topfen",
        "vanille",
        "zimt",
        "marzipan",
        "karamell",
        "caramel",
        "kokos",
        "ananas",
        "feige",
        "traube",
        "tatin",
    )

    /** Endet der Titel darauf, ist es Beiwerk: "Tomatensauce", "Kraeuterbutter", "Bauernbrot". */
    private val SIDE_ENDINGS = listOf(
        "sauce",
        "saucen",
        "sosse",
        "dip",
        "dips",
        "dressing",
        "vinaigrette",
        "marinade",
        "pesto",
        "chutney",
        "ketchup",
        "mayonnaise",
        "mayo",
        "aioli",
        "relish",
        "jus",
        "fond",
        "bouillon",
        "bruehe",
        "gewuerzmischung",
        "gewuerz",
        "rub",
        "konfituere",
        "marmelade",
        "gelee",
        "aufstrich",
        "butter",
        "oel",
        "essig",
        "teig",
        "brot",
        "brote",
        "zopf",
        "broetchen",
        "broetli",
        "buerli",
        "weggli",
        "gipfeli",
        "focaccia",
        "knaeckebrot",
        "cracker",
        "grissini",
        "chips",
        "popcorn",
        "muesli",
        "mueesli",
        "granola",
        "porridge",
        "hummus",
        "guacamole",
        "tzatziki",
        "tapenade",
        "raita",
        "salsa",
        "paste",
        "pulver",
        "croutons",
        "streusel",
        "glasur",
    )

    private val SIDE_WORDS = setOf(
        "sauce",
        "dip",
        "pesto",
        "dressing",
        "marinade",
        "grundrezept",
        "grundteig",
        "overnight",
        "porridge",
        "muesli",
        "salsa",
        "hummus",
        "guacamole",
        "tzatziki",
        "aioli",
        "chutney",
        "vinaigrette",
        "bouillon",
        "granola",
    )

    private val NOT_A_SIDE = setOf("kraftbruehe")

    /** Steht eines davon vorn, ist "…sauce" am Ende nur die Beigabe: "Pasta Tomatensauce". */
    private val MEAL_ANCHORS = setOf(
        "pasta",
        "spaghetti",
        "nudeln",
        "penne",
        "tagliatelle",
        "reis",
        "risotto",
        "poulet",
        "huhn",
        "fleisch",
        "fisch",
        "lachs",
        "gnocchi",
        "kartoffeln",
        "steak",
        "filet",
        "schnitzel",
        "braten",
        "ravioli",
        "tortellini",
        "burger",
        "sandwich",
        "toast",
        "salat",
        "suppe",
        "eintopf",
        "curry",
        "pfanne",
        "auflauf",
        "gratin",
        "wrap",
        "wraps",
        "pizza",
        "quiche",
        "omelette",
        "roesti",
        "spaetzle",
        "knoepfli",
        "hoernli",
        "lasagne",
        "fish",
        "chicken",
        "bowl",
        "tacos",
        "nachos",
        "bruschetta",
        "crostini",
    )

    private val DRINK_TAGS = setOf(
        "getraenk",
        "getraenke",
        "drink",
        "drinks",
        "cocktail",
        "cocktails",
        "smoothie",
        "smoothies",
        "bowle",
        "punsch",
        "longdrinks",
        "mocktails",
    )

    private val MAIN_TAGS = setOf(
        "hauptgericht",
        "hauptgerichte",
        "hauptspeise",
        "hauptspeisen",
        "hauptgang",
        "mittagessen",
        "abendessen",
        "znacht",
        "zmittag",
        "hauptmahlzeit",
    )

    private val DESSERT_TAGS = setOf(
        "dessert",
        "desserts",
        "nachspeise",
        "nachspeisen",
        "nachtisch",
        "suessspeise",
        "suessspeisen",
        "kuchen",
        "torte",
        "torten",
        "gebaeck",
        "guetzli",
        "suesses",
        "glace",
        "weihnachtsguetzli",
        "patisserie",
    )

    private val SIDE_TAGS = setOf(
        "beilage",
        "beilagen",
        "sauce",
        "saucen",
        "dip",
        "dips",
        "brot",
        "aufstrich",
        "aufstriche",
        "konfituere",
        "grundrezept",
        "grundrezepte",
        "eingemachtes",
        "einmachen",
    )
}

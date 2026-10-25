package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.IngredientNameNormalizer
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.Mood
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
        val words = recipe.ingredients
            .flatMap { words(it.canonicalName ?: it.name) + words(it.name) }
            .toSet()
        val title = words(recipe.title).toSet()
        val all = words + title
        val text = IngredientNameNormalizer.normalize(
            recipe.title + " " + recipe.tags.joinToString(" ") + " " + recipe.instructions,
        )

        val hasMeat = all.any(::isMeat)
        val hasFish = all.any { word -> FISH.any { word.contains(it) } }
        val hasDairy = all.any(::isDairy)
        val hasEgg = all.any { it in EGG }
        val hasHoney = all.any { it.contains("honig") }

        val diet = when {
            hasMeat -> DietClass.FLEISCH
            hasFish -> DietClass.FISCH
            hasDairy || hasEgg || hasHoney -> DietClass.VEGETARISCH
            else -> DietClass.VEGAN
        }

        val allergens = buildSet {
            if (all.any(::hasGluten)) add(Intolerance.GLUTEN)
            if (hasDairy) add(Intolerance.LAKTOSE)
            if (all.any { word ->
                    NUTS.any { word.contains(it) } && NUTS_EXCLUDED.none { word.contains(it) }
                }
            ) {
                add(Intolerance.NUESSE)
            }
            if (hasEgg) add(Intolerance.EIER)
            if (all.any { word -> SOY.any { word.contains(it) } }) add(Intolerance.SOJA)
            if (all.any { word -> SEAFOOD.any { word.contains(it) } }) add(Intolerance.MEERESFRUECHTE)
        }

        return RecipeTraits(
            diet = diet,
            allergens = allergens,
            moods = moodsOf(recipe, all, title, diet, text),
            requiredAppliances = appliancesOf(text),
            lowCarb = all.none { word -> CARBS.any { word.contains(it) } },
        )
    }

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
        // Alles, was keines der Geraete verlangt, geht auf dem Herd.
        if (isEmpty() || STOVE.any { text.contains(it) }) add(Appliance.HERD)
    }

    private fun isMeat(word: String): Boolean =
        MEAT.any { word.contains(it) } &&
            PLANT_MEAT.none { word.contains(it) } &&
            FISH.none { word.contains(it) }

    private fun hasGluten(word: String): Boolean =
        GLUTEN.any { word.contains(it) } && GLUTEN_FREE.none { word.contains(it) }

    private fun isDairy(word: String): Boolean =
        DAIRY.any { word.contains(it) } && PLANT_BASED.none { word.contains(it) }

    private fun words(text: String): List<String> =
        IngredientNameNormalizer.normalize(text).split(' ').filter { it.length >= 2 }

    private companion object {
        const val QUICK_MINUTES = 30

        val MEAT = listOf(
            "fleisch",
            "poulet",
            "huhn",
            "haehnchen",
            "huehn",
            "rind",
            "schwein",
            "hack",
            "speck",
            "schinken",
            "wurst",
            "salami",
            "lamm",
            "kalb",
            "bratwurst",
            "chorizo",
            "entenbrust",
            "entenkeule",
            "pute",
            "truthahn",
            "bacon",
            "leber",
            "gulasch",
            "geschnetzelt",
            "steak",
            "entrecote",
            "cervelat",
            "mortadella",
            "pancetta",
            "kotelett",
            "schnitzel",
            "ragout",
            "gehackt",
            "pouletbrust",
            "wiener",
            "landjaeger",
        )
        val PLANT_MEAT = listOf("veggie", "vegi", "vegan", "tofu", "seitan", "soja", "fischstaebchen")
        val FISH = listOf(
            "fisch",
            "lachs",
            "thunfisch",
            "forelle",
            "kabeljau",
            "dorsch",
            "sardelle",
            "anchovis",
            "seelachs",
            "pangasius",
            "crevette",
            "garnele",
            "scampi",
            "muschel",
            "tintenfisch",
            "calamari",
            "krabbe",
            "hummer",
            "krebs",
            "meeresfruechte",
            "auster",
            "makrele",
            "hering",
            "zander",
            "egli",
            "felchen",
        )
        val DAIRY = listOf(
            "milch",
            "rahm",
            "sahne",
            "butter",
            "kaese",
            "quark",
            "joghurt",
            "mozzarella",
            "parmesan",
            "gruyere",
            "sbrinz",
            "mascarpone",
            "ricotta",
            "creme fraiche",
            "emmentaler",
            "feta",
            "halloumi",
            "schmand",
            "buttermilch",
            "kefir",
            "raclette",
            "gorgonzola",
            "pecorino",
            "burrata",
        )
        val PLANT_BASED = listOf(
            "kokos",
            "hafer",
            "mandel",
            "soja",
            "reismilch",
            "erdnuss",
            "kakao",
            "vegan",
            "sojajoghurt",
            "cashew",
            "nussbutter",
            "shea",
        )
        val EGG = setOf("ei", "eier", "eigelb", "eiweiss", "eiern", "eidotter", "eischnee", "spiegelei", "rührei")
        val GLUTEN = listOf(
            "mehl",
            "weizen",
            "nudel",
            "pasta",
            "spaghetti",
            "penne",
            "hoernli",
            "brot",
            "semmel",
            "paniermehl",
            "couscous",
            "bulgur",
            "gerste",
            "roggen",
            "dinkel",
            "teigwaren",
            "tortilla",
            "teig",
            "gnocchi",
            "lasagne",
            "toast",
            "broetchen",
            "bier",
            "knoedel",
            "spaetzle",
            "tagliatelle",
            "fusilli",
            "maccheroni",
            "ravioli",
            "tortelloni",
            "tortellini",
            "panade",
            "semmelbroesel",
            "zwieback",
            "wrap",
            "fladenbrot",
            "burgerbroetchen",
            "croutons",
            "sojasauce",
            "seitan",
            "grieess",
            "griess",
        )
        val NUTS = listOf(
            "nuss",
            "nuesse",
            "mandel",
            "haselnuss",
            "walnuss",
            "cashew",
            "pistazie",
            "pecan",
            "erdnuss",
            "marzipan",
            "nutella",
            "pinienkern",
            "macadamia",
            "paranuss",
        )
        val GLUTEN_FREE = listOf("buchweizen", "mais", "kichererbsen", "kartoffel", "reismehl", "mandel", "glutenfrei")
        val NUTS_EXCLUDED = listOf("muskat", "kokos")
        val SOY = listOf("soja", "tofu", "edamame", "tempeh", "miso", "sojasauce", "tamari")
        val SEAFOOD = listOf(
            "garnele",
            "crevette",
            "scampi",
            "muschel",
            "tintenfisch",
            "calamari",
            "krabbe",
            "hummer",
            "krebs",
            "meeresfruechte",
            "auster",
        )
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

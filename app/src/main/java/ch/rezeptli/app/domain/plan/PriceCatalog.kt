package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.IngredientCategory
import ch.rezeptli.app.domain.model.IngredientNameNormalizer
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.profile.StoreChain
import ch.rezeptli.app.domain.shopping.UnitConverter
import ch.rezeptli.app.domain.shopping.UnitConverter.dimension
import ch.rezeptli.app.domain.shopping.UnitDimension
import kotlin.math.ceil
import kotlin.math.round

/** Eine Packung, wie man sie im Regal kauft. */
data class Pack(
    val label: String,
    /** Inhalt in Gramm, Millilitern oder Stueck. */
    val size: Double,
    val price: Double,
)

/** Was auf dem Einkaufszettel steht: wie viele Packungen von welcher Sorte, zu welchem Preis. */
data class PackChoice(
    val packs: Int,
    val pack: Pack,
) {
    val total: Double get() = round(packs * pack.price * CENTS) / CENTS
    val text: String get() = if (packs == 1) pack.label else "$packs × ${pack.label}"

    private companion object {
        const val CENTS = 100.0
    }
}

/**
 * Richtpreise und Packungsgroessen.
 *
 * Das sind Schaetzwerte fuer die Schweiz (Stand 2026, Niveau Migros/Coop), keine Live-Preise:
 * Die App fragt kein Geschaeft ab. [StoreChain.priceFactor] verschiebt das Niveau je Laden.
 * Die Oberflaeche zeigt alle Summen deshalb mit "ca.".
 */
object PriceCatalog {
    /** Rechnet eine Zutat in die Packungen um, die man dafuer kaufen muss. */
    fun choose(item: ShoppingItem, store: StoreChain?): PackChoice {
        val factor = store?.priceFactor ?: 1.0
        val key = IngredientNameNormalizer.normalize(item.name)
        val words = key.split(' ')
        val rule = RULES.firstOrNull { rule -> rule.keywords.any { keyword -> words.any { matches(it, keyword) } } }
        val pack = (rule?.pack ?: defaultPack(item.category, item.unit.dimension))

        val needed = neededIn(item, pack.dimension)
        val count = if (needed == null || needed <= 0.0) 1 else ceil(needed / pack.size - TOLERANCE).toInt().coerceAtLeast(1)

        return PackChoice(
            packs = count,
            pack = Pack(pack.label, pack.size, round(pack.price * factor * CENTS) / CENTS),
        )
    }

    /**
     * Der Anteil einer Packung, den dieser Posten verbraucht - fuer den Preis einer einzelnen Mahlzeit.
     *
     * Ganze Packungen zaehlen erst im Wocheneinkauf; eine einzelne Mahlzeit bezahlt nur, was sie isst.
     */
    fun fractionalCost(item: ShoppingItem, store: StoreChain?): Double {
        val choice = choose(item, store)
        val key = IngredientNameNormalizer.normalize(item.name).split(' ')
        val spec = RULES.firstOrNull { rule -> rule.keywords.any { k -> key.any { matches(it, k) } } }?.pack
            ?: defaultPack(item.category, item.unit.dimension)
        val needed = neededIn(item, spec.dimension) ?: return choice.pack.price * MIN_SHARE
        val share = (needed / spec.size).coerceIn(MIN_SHARE, choice.packs.toDouble())
        return choice.pack.price * share
    }

    /** Kurze Stichwoerter ("ei", "oel") muessen ein ganzes Wort sein, sonst passt "ei" auf "reis". */
    private fun matches(word: String, keyword: String): Boolean =
        word == keyword || (keyword.length >= MIN_PARTIAL && word.contains(keyword))

    private fun neededIn(item: ShoppingItem, dimension: UnitDimension): Double? {
        val amount = item.amount ?: return null
        return when (item.unit.dimension) {
            UnitDimension.MASS, UnitDimension.VOLUME -> UnitConverter.toBase(amount, item.unit)
                .takeIf { item.unit.dimension == dimension }
            UnitDimension.SPOON -> if (dimension == UnitDimension.MASS || dimension == UnitDimension.VOLUME) {
                // Ein Teeloeffel fasst rund 5 g oder ml.
                UnitConverter.toBase(amount, item.unit) * TEASPOON
            } else {
                null
            }
            UnitDimension.COUNT -> amount.takeIf { dimension == UnitDimension.COUNT }
            UnitDimension.DISCRETE -> discreteAs(item.unit, amount, dimension)
        }
    }

    private fun discreteAs(unit: IngredientUnit, amount: Double, dimension: UnitDimension): Double? = when {
        dimension == UnitDimension.COUNT -> when (unit) {
            IngredientUnit.ZEHE -> amount / CLOVES_PER_BULB
            IngredientUnit.PRISE, IngredientUnit.MESSERSPITZE -> 0.0
            else -> amount
        }
        unit == IngredientUnit.TASSE -> amount * CUP
        else -> null
    }

    private data class Rule(val keywords: List<String>, val pack: PackSpec)

    private data class PackSpec(
        val label: String,
        val size: Double,
        val price: Double,
        val dimension: UnitDimension,
    )

    private fun mass(label: String, grams: Double, price: Double) = PackSpec(label, grams, price, UnitDimension.MASS)

    private fun volume(label: String, ml: Double, price: Double) = PackSpec(label, ml, price, UnitDimension.VOLUME)

    private fun count(label: String, pieces: Double, price: Double) = PackSpec(label, pieces, price, UnitDimension.COUNT)

    private fun defaultPack(category: IngredientCategory, dimension: UnitDimension): PackSpec = when (dimension) {
        UnitDimension.COUNT, UnitDimension.DISCRETE -> count("Stück", 1.0, CATEGORY_PIECE[category] ?: 0.9)
        UnitDimension.VOLUME -> volume("5 dl", 500.0, 2.2)
        else -> when (category) {
            IngredientCategory.GEMUESE -> mass("500 g", 500.0, 2.4)
            IngredientCategory.FRUECHTE -> mass("500 g", 500.0, 3.2)
            IngredientCategory.MILCHPRODUKTE -> mass("200 g", 200.0, 3.2)
            IngredientCategory.FLEISCH_FISCH -> mass("400 g", 400.0, 8.9)
            IngredientCategory.TEIGWAREN_GETREIDE -> mass("500 g", 500.0, 2.1)
            IngredientCategory.KONSERVEN_SAUCEN -> mass("400 g", 400.0, 1.6)
            IngredientCategory.BACKEN_SUESSES -> mass("250 g", 250.0, 2.6)
            IngredientCategory.GEWUERZE_KRAEUTER -> mass("Glas", 50.0, 2.8)
            IngredientCategory.TIEFKUEHL -> mass("500 g", 500.0, 4.2)
            IngredientCategory.GETRAENKE -> volume("1 l", 1000.0, 2.0)
            IngredientCategory.SONSTIGES -> mass("250 g", 250.0, 2.9)
        }
    }

    private val CATEGORY_PIECE = mapOf(
        IngredientCategory.GEMUESE to 0.8,
        IngredientCategory.FRUECHTE to 0.7,
        IngredientCategory.MILCHPRODUKTE to 2.2,
        IngredientCategory.FLEISCH_FISCH to 4.5,
        IngredientCategory.KONSERVEN_SAUCEN to 1.5,
        IngredientCategory.GEWUERZE_KRAEUTER to 2.8,
    )

    /** Von lang nach kurz: "olivenoel" muss vor "oel" greifen. */
    private val RULES: List<Rule> = listOf(
        Rule(listOf("olivenoel"), volume("5 dl", 500.0, 7.9)),
        Rule(listOf("rapsoel", "sonnenblumenoel", "bratoel", "oel"), volume("1 l", 1000.0, 4.2)),
        Rule(listOf("essig"), volume("5 dl", 500.0, 2.3)),
        Rule(listOf("sojasauce"), volume("2 dl", 200.0, 2.9)),
        Rule(listOf("kokosmilch"), volume("Dose 4 dl", 400.0, 1.9)),
        Rule(listOf("passata", "tomatensauce"), volume("Packung 7 dl", 700.0, 2.2)),
        Rule(listOf("bouillon", "bruehe", "fond"), mass("Würfel-Packung", 120.0, 2.9)),
        Rule(listOf("milch"), volume("1 l", 1000.0, 1.7)),
        Rule(listOf("rahm", "sahne"), volume("2.5 dl", 250.0, 2.2)),
        Rule(listOf("joghurt"), mass("180 g", 180.0, 0.9)),
        Rule(listOf("quark"), mass("500 g", 500.0, 3.2)),
        Rule(listOf("butter"), mass("250 g", 250.0, 3.6)),
        Rule(listOf("mozzarella"), mass("150 g", 150.0, 1.5)),
        Rule(listOf("parmesan", "sbrinz", "gruyere", "reibkaese"), mass("100 g", 100.0, 3.1)),
        Rule(listOf("feta", "ziegenkaese"), mass("200 g", 200.0, 3.3)),
        Rule(listOf("kaese"), mass("200 g", 200.0, 4.2)),
        Rule(listOf("eier", "ei"), count("6 Eier", 6.0, 3.1)),
        Rule(listOf("zwiebel"), mass("1 kg", 1000.0, 3.2)),
        Rule(listOf("kartoffel"), mass("2.5 kg", 2500.0, 5.1)),
        Rule(listOf("knoblauch"), count("Knolle", 1.0, 0.8)),
        Rule(listOf("tomate"), mass("500 g", 500.0, 3.5)),
        Rule(listOf("pelati", "dosentomate", "gehackte tomaten"), mass("Dose 400 g", 400.0, 1.3)),
        Rule(listOf("karotte", "rueebli"), mass("1 kg", 1000.0, 2.2)),
        Rule(listOf("paprika", "peperoni"), count("Stück", 1.0, 1.4)),
        Rule(listOf("zucchini", "zucchetti"), count("Stück", 1.0, 1.1)),
        Rule(listOf("gurke"), count("Stück", 1.0, 1.0)),
        Rule(listOf("salat"), count("Kopf", 1.0, 1.9)),
        Rule(listOf("zitrone", "limette"), count("Stück", 1.0, 0.6)),
        Rule(listOf("avocado"), count("Stück", 1.0, 1.8)),
        Rule(listOf("banane"), mass("1 kg", 1000.0, 2.6)),
        Rule(listOf("apfel", "aepfel"), mass("1 kg", 1000.0, 3.9)),
        Rule(listOf("reis"), mass("1 kg", 1000.0, 3.4)),
        Rule(listOf("mehl"), mass("1 kg", 1000.0, 1.6)),
        Rule(listOf("zucker"), mass("1 kg", 1000.0, 1.7)),
        Rule(listOf("haferflocken"), mass("500 g", 500.0, 2.0)),
        Rule(listOf("spaghetti", "penne", "nudel", "pasta", "hoernli", "fusilli", "teigwaren"), mass("500 g", 500.0, 1.9)),
        Rule(listOf("brot"), mass("500 g", 500.0, 3.4)),
        Rule(listOf("tortilla", "wrap"), count("8er-Packung", 8.0, 3.2)),
        Rule(listOf("linsen", "kichererbsen", "bohnen"), mass("Dose 400 g", 400.0, 1.4)),
        Rule(listOf("hackfleisch", "gehackt"), mass("400 g", 400.0, 7.9)),
        Rule(listOf("poulet", "huhn", "haehnchen"), mass("400 g", 400.0, 9.6)),
        Rule(listOf("lachs"), mass("200 g", 200.0, 7.9)),
        Rule(listOf("thunfisch"), mass("Dose 150 g", 150.0, 2.1)),
        Rule(listOf("speck", "schinken", "bacon"), mass("150 g", 150.0, 3.4)),
        Rule(listOf("wurst"), mass("300 g", 300.0, 5.2)),
        Rule(listOf("tofu"), mass("200 g", 200.0, 3.1)),
        Rule(listOf("salz"), mass("500 g", 500.0, 0.9)),
        Rule(listOf("pfeffer", "paprikapulver", "curry", "kreuzkuemmel", "zimt", "oregano", "thymian", "chili"), mass("Glas", 40.0, 2.9)),
        Rule(listOf("petersilie", "basilikum", "schnittlauch", "koriander", "dill", "minze"), count("Bund", 1.0, 1.9)),
        Rule(listOf("wasser"), volume("1 l", 1000.0, 0.0)),
    ).map { it.copy(keywords = it.keywords.map(IngredientNameNormalizer::normalize)) }

    private const val MIN_SHARE = 0.1
    private const val MIN_PARTIAL = 4
    private const val TOLERANCE = 1e-6
    private const val CENTS = 100.0
    private const val TEASPOON = 5.0
    private const val CLOVES_PER_BULB = 10.0
    private const val CUP = 240.0
}

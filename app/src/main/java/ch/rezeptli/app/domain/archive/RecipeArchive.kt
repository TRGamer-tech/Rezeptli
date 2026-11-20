package ch.rezeptli.app.domain.archive

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.Market
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Mood
import ch.rezeptli.app.domain.profile.ProfileEdits
import ch.rezeptli.app.domain.profile.StoreChain
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.settings.ThemeChoice
import ch.rezeptli.app.domain.steps.RecipeStep
import ch.rezeptli.app.domain.timer.CookingTimer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Warum sich eine Datei nicht einlesen liess. */
enum class ArchiveError {
    /** Die Datei liess sich nicht oeffnen oder nicht zu Ende lesen. */
    UNREADABLE,

    /** Es ist keine Exportdatei von Rezeptli. */
    NOT_AN_ARCHIVE,

    /** Sie stammt aus einer neueren App, die mehr kann als diese. */
    NEWER_VERSION,

    /** Sie ist groesser, als eine Rezeptsammlung je sein kann. */
    TOO_LARGE,

    /** Sie ist in Ordnung, enthaelt aber kein einziges Rezept. */
    EMPTY,
}

class ArchiveException(
    val error: ArchiveError,
) : Exception(error.name)

/**
 * Ein Rezept, wie es in einer Exportdatei steht: Das Foto ist dort ein Verweis - auf eine
 * Bilddatei im selben Archiv ("fotos/3.jpg") oder auf eine Adresse im Netz.
 */
data class ArchivedRecipe(
    val recipe: Recipe,
    val photo: String?,
)

/**
 * Die Vorlieben, wie sie in einer Exportdatei stehen: das Profil und wie die App aussieht.
 *
 * [theme] und [reduceMotion] sind `null`, wenn die Datei dazu nichts sagt. Ob die Einrichtung
 * schon durchlaufen wurde, reist nicht mit - das ist eine Sache des Telefons, nicht der Person.
 */
data class ArchivedPreferences(
    val profile: UserProfile,
    val theme: ThemeChoice? = null,
    val reduceMotion: Boolean? = null,
)

/** Alles, was in einer Exportdatei steht. */
data class ArchiveContent(
    val recipes: List<ArchivedRecipe>,
    /** `null`, wenn die Datei ohne Vorlieben geschrieben wurde. */
    val preferences: ArchivedPreferences? = null,
)

/**
 * Das Format der Exportdatei.
 *
 * Eine Exportdatei ist ein ZIP mit `rezepte.json` und den Fotos im Ordner `fotos/`. Der
 * Text ist bewusst gewoehnliches, lesbares JSON mit deutschen Feldnamen: Die Rezepte gehoeren
 * der Person, die sie gesammelt hat - sie soll sie auch ohne diese App lesen koennen.
 *
 * Gelesen wird nachsichtig und misstrauisch zugleich. Nachsichtig: Fehlt ein Feld oder ist
 * ein Wert unbekannt, gilt eine vernuenftige Vorgabe. Misstrauisch: Die Datei kann von
 * irgendwoher kommen, also wird alles auf eine sinnvolle Groesse gestutzt.
 */
object RecipeArchive {
    const val FORMAT = "rezeptli-rezepte"
    const val VERSION = 1
    const val JSON_ENTRY = "rezepte.json"
    const val PHOTO_FOLDER = "fotos/"

    private val json = Json { prettyPrint = true }

    /** Ob [value] auf etwas im Netz zeigt - solche Fotos und Quellen bleiben, was sie sind. */
    fun isWebAddress(value: String?): Boolean =
        value != null && (value.startsWith("https://") || value.startsWith("http://"))

    // ------------------------------------------------------------------------------ schreiben

    fun encode(recipes: List<ArchivedRecipe>, exportedAt: Long, preferences: ArchivedPreferences? = null): String {
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportiert", exportedAt)
            // Ein neues Feld, keine neue Version: Wer es nicht kennt, ueberliest es und bekommt die Rezepte.
            preferences?.let { put("vorlieben", it.toJson()) }
            put("rezepte", buildJsonArray { recipes.forEach { add(it.toJson()) } })
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun ArchivedPreferences.toJson(): JsonObject = buildJsonObject {
        fun list(values: Iterable<String>) = buildJsonArray { values.forEach { add(it) } }

        if (profile.firstName.isNotBlank()) put("vorname", profile.firstName)
        profile.country?.let { put("land", it.code) }
        put("kuechen", list(profile.cuisines.map { it.name }))
        put("ernaehrung", list(profile.diets.map { it.name }))
        put("unvertraeglichkeiten", list(profile.intolerances.map { it.name }))
        put("abneigungen", list(profile.dislikes.map { it.name }))
        put("eigeneAbneigungen", list(profile.customDislikes))
        profile.householdSize?.let { put("personen", it) }
        // Die Reihenfolge zaehlt: Der erste Laden ist der Hauptladen.
        put("laeden", list(profile.stores.map { it.name }))
        put("kochtage", list(profile.cookDays.sortedBy { it.ordinal }.map { it.name }))
        put("mahlzeiten", list(profile.mealSlots.sortedBy { it.ordinal }.map { it.name }))
        profile.weeklyBudget?.let { put("wochenbudget", it) }
        put("stimmungen", list(profile.moods.map { it.name }))
        put("geraete", list(profile.appliances.map { it.name }))
        theme?.let { put("aussehen", it.name) }
        reduceMotion?.let { put("wenigerBewegung", it) }
    }

    private fun ArchivedRecipe.toJson(): JsonObject = buildJsonObject {
        put("titel", recipe.title)
        put("zubereitung", recipe.instructions)
        put(
            "schritte",
            buildJsonArray {
                recipe.steps.sortedBy { it.position }.forEach { step ->
                    add(
                        buildJsonObject {
                            put("text", step.text)
                            step.timerMinutes?.let { put("timerMinuten", it) }
                        },
                    )
                }
            },
        )
        put(
            "zutaten",
            buildJsonArray {
                recipe.ingredients.sortedBy { it.position }.forEach { ingredient ->
                    add(
                        buildJsonObject {
                            put("name", ingredient.name)
                            ingredient.amount?.let { put("menge", it) }
                            if (ingredient.unit != IngredientUnit.NONE) put("einheit", ingredient.unit.name)
                            ingredient.note?.let { put("notiz", it) }
                            ingredient.canonicalName?.let { put("grundname", it) }
                        },
                    )
                }
            },
        )
        put("tags", buildJsonArray { recipe.tags.forEach { add(it) } })
        recipe.prepTimeMinutes?.let { put("zeitMinuten", it) }
        recipe.servings?.let { put("portionen", it) }
        // Nur, was die Quelle genannt hat - was die App schaetzt, rechnet sie nach dem Einlesen neu.
        recipe.kcalPerServing?.let { put("kcalProPortion", it) }
        recipe.proteinPerServing?.let { put("eiweissProPortion", it) }
        photo?.let { put("foto", it) }
        recipe.sourceUrl?.let { put("quelleUrl", it) }
        recipe.sourceName?.let { put("quelleName", it) }
        if (recipe.createdAt > 0) put("erstellt", recipe.createdAt)
        recipe.lastCookedAt?.let { put("zuletztGekocht", it) }
    }

    // --------------------------------------------------------------------------------- lesen

    /** Wirft [ArchiveException], wenn [text] keine Exportdatei ist. */
    fun decode(text: String): ArchiveContent {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: throw ArchiveException(ArchiveError.NOT_AN_ARCHIVE)

        if (root.string("format") != FORMAT) throw ArchiveException(ArchiveError.NOT_AN_ARCHIVE)
        val version = root.int("version") ?: throw ArchiveException(ArchiveError.NOT_AN_ARCHIVE)
        if (version > VERSION) throw ArchiveException(ArchiveError.NEWER_VERSION)

        return ArchiveContent(
            recipes = root
                .objects("rezepte")
                .take(MAX_RECIPES)
                .mapNotNull { it.toRecipe() },
            preferences = (root["vorlieben"] as? JsonObject)?.toPreferences(),
        )
    }

    /**
     * Liest die Vorlieben - mit denselben Grenzen, die beim Antippen in der App gelten.
     *
     * Was die App nicht kennt (eine Kueche aus einer neueren Version, ein Laden aus einem
     * anderen Land), faellt weg, statt etwas Unsinniges zu hinterlassen: hoechstens eine
     * Ernaehrungsform, hoechstens drei Laeden und nur solche, die es im Land gibt.
     */
    private fun JsonObject.toPreferences(): ArchivedPreferences {
        val country = Country.fromCode(string("land"))
        val market = Market.of(country)
        val diets = names("ernaehrung").mapNotNull(Diet::fromName)
        val mainDiet = diets.firstOrNull { it in ProfileEdits.MAIN_DIETS }

        var profile = UserProfile(
            country = country,
            cuisines = names("kuechen").mapNotNullTo(linkedSetOf(), Cuisine::fromName),
            diets = diets.filterTo(linkedSetOf()) { it !in ProfileEdits.MAIN_DIETS || it == mainDiet },
            intolerances = names("unvertraeglichkeiten").mapNotNullTo(linkedSetOf(), Intolerance::fromName),
            dislikes = names("abneigungen").mapNotNullTo(linkedSetOf(), Dislike::fromName),
            householdSize = int("personen")?.takeIf { it in 1..UserProfile.MAX_HOUSEHOLD_SIZE },
            stores = names("laeden")
                .mapNotNull(StoreChain::fromName)
                .distinct()
                .filter { it in market.stores }
                .take(UserProfile.MAX_STORES),
            cookDays = names("kochtage").mapNotNullTo(linkedSetOf(), Weekday::fromName),
            mealSlots = names("mahlzeiten").mapNotNullTo(linkedSetOf(), MealSlot::fromName),
            weeklyBudget = int("wochenbudget")?.takeIf { it in UserProfile.MIN_BUDGET..UserProfile.MAX_BUDGET },
            moods = names("stimmungen").mapNotNull(Mood::fromName).take(UserProfile.MAX_MOODS).toSet(),
            appliances = names("geraete").mapNotNullTo(linkedSetOf(), Appliance::fromName),
        )
        profile = ProfileEdits.name(profile, string("vorname").orEmpty().trim())
        // Eigene Stichworte laufen durch dieselbe Pruefung wie beim Eintippen: Laenge, Anzahl, keine Dubletten.
        names("eigeneAbneigungen").take(MAX_WORDS).forEach { profile = ProfileEdits.addCustomDislike(profile, it) }

        return ArchivedPreferences(
            profile = profile,
            theme = string("aussehen")?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } },
            reduceMotion = this["wenigerBewegung"].primitive()?.content?.toBooleanStrictOrNull(),
        )
    }

    private fun JsonObject.toRecipe(): ArchivedRecipe? {
        // Ohne Titel ist es kein Rezept - es liesse sich in der Liste nicht einmal antippen.
        val title = string("titel")?.take(MAX_TITLE) ?: return null
        val steps = objects("schritte")
            .take(MAX_STEPS)
            .mapNotNull { step ->
                val text = step.string("text")?.take(MAX_STEP) ?: return@mapNotNull null
                text to step.int("timerMinuten")?.takeIf { it in 1..CookingTimer.MAX_MINUTES }
            }.mapIndexed { index, (text, timer) -> RecipeStep(position = index, text = text, timerMinutes = timer) }

        val ingredients = objects("zutaten")
            .take(MAX_INGREDIENTS)
            .mapNotNull { ingredient ->
                val name = ingredient.string("name")?.take(MAX_NAME) ?: return@mapNotNull null
                Ingredient(
                    name = name,
                    amount = ingredient.double("menge"),
                    unit = IngredientUnit.entries.firstOrNull { it.name == ingredient.string("einheit") }
                        ?: IngredientUnit.NONE,
                    note = ingredient.string("notiz")?.take(MAX_NAME),
                    canonicalName = ingredient.string("grundname")?.take(MAX_NAME),
                )
            }.mapIndexed { index, ingredient -> ingredient.copy(position = index) }

        val recipe = Recipe(
            title = title,
            // Fehlt der Fliesstext, wird er aus den Schritten zusammengesetzt - so steht das
            // Rezept auch beim Bearbeiten vollstaendig da.
            instructions = (string("zubereitung") ?: steps.joinToString("\n\n") { it.text }).take(MAX_INSTRUCTIONS),
            steps = steps,
            ingredients = ingredients,
            tags = names("tags").map { it.take(MAX_TAG) }.distinct().take(MAX_TAGS),
            prepTimeMinutes = int("zeitMinuten")?.takeIf { it in 1..MAX_PREP_MINUTES },
            servings = int("portionen")?.takeIf { it in 1..MAX_SERVINGS },
            kcalPerServing = int("kcalProPortion")?.takeIf { it in 1..MAX_KCAL },
            proteinPerServing = double("eiweissProPortion")?.takeIf { it <= MAX_PROTEIN },
            sourceUrl = string("quelleUrl")?.takeIf { isWebAddress(it) }?.take(MAX_ADDRESS),
            sourceName = string("quelleName")?.take(MAX_NAME),
            createdAt = long("erstellt")?.takeIf { it > 0 } ?: 0L,
            lastCookedAt = long("zuletztGekocht")?.takeIf { it > 0 },
        )
        val photo = string("foto")?.takeIf {
            it.length <= MAX_ADDRESS &&
                (isWebAddress(it) || it.startsWith(PHOTO_FOLDER))
        }
        return ArchivedRecipe(recipe, photo)
    }

    private fun JsonElement?.primitive(): JsonPrimitive? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }

    private fun JsonObject.string(name: String): String? = this[name].primitive()?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(name: String): Int? = this[name].primitive()?.content?.toIntOrNull()

    private fun JsonObject.long(name: String): Long? = this[name].primitive()?.content?.toLongOrNull()

    private fun JsonObject.double(name: String): Double? =
        this[name]
            .primitive()
            ?.content
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0 }

    private fun JsonObject.objects(name: String): List<JsonObject> =
        (this[name] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

    private fun JsonObject.names(name: String): List<String> =
        (this[name] as? JsonArray)?.mapNotNull { it.primitive()?.content?.takeIf(String::isNotBlank) }.orEmpty()

    private const val MAX_RECIPES = 20_000
    private const val MAX_TITLE = 300
    private const val MAX_INSTRUCTIONS = 100_000
    private const val MAX_STEPS = 200
    private const val MAX_STEP = 10_000
    private const val MAX_INGREDIENTS = 300
    private const val MAX_NAME = 300
    private const val MAX_TAGS = 50
    private const val MAX_TAG = 80
    private const val MAX_ADDRESS = 2_000
    private const val MAX_PREP_MINUTES = 24 * 60
    private const val MAX_SERVINGS = 100
    private const val MAX_KCAL = 4_000
    private const val MAX_PROTEIN = 300.0
    private const val MAX_WORDS = 100
}

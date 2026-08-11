package ch.rezeptli.app.presentation.common.form

import ch.rezeptli.app.domain.model.AmountFormatter
import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.SwissIngredientSynonyms
import ch.rezeptli.app.domain.parser.ParseConfidence
import ch.rezeptli.app.domain.parser.ParsedIngredient
import ch.rezeptli.app.domain.parser.ParsedRecipe
import ch.rezeptli.app.domain.steps.RecipeStep

/**
 * Eine Zutatenzeile im Formular.
 *
 * Mengen liegen als Text vor, weil das Eingabefeld auch Zwischenzustaende wie "1," halten
 * koennen muss. [needsReview] markiert Zeilen, die der Import-Parser nicht sicher erkannt
 * hat - die UI hebt sie hervor, damit sie nicht ungeprueft gespeichert werden.
 */
data class IngredientDraft(
    val key: Long,
    val name: String = "",
    val amountText: String = "",
    val unit: IngredientUnit = IngredientUnit.NONE,
    val note: String = "",
    val canonicalName: String? = null,
    val needsReview: Boolean = false,
    val rawLine: String? = null,
) {
    val amount: Double?
        get() = amountText.trim().replace(',', '.').toDoubleOrNull()

    fun toIngredient(position: Int): Ingredient = Ingredient(
        name = name.trim(),
        amount = amount,
        unit = unit,
        note = note.trim().takeIf { it.isNotEmpty() },
        canonicalName = canonicalName ?: SwissIngredientSynonyms.canonicalFor(name),
        position = position,
    )

    companion object {
        fun from(ingredient: Ingredient, key: Long): IngredientDraft = IngredientDraft(
            key = key,
            name = ingredient.name,
            amountText = AmountFormatter.formatForInput(ingredient.amount),
            unit = ingredient.unit,
            note = ingredient.note.orEmpty(),
            canonicalName = ingredient.canonicalName,
        )

        fun from(parsed: ParsedIngredient, key: Long): IngredientDraft = IngredientDraft(
            key = key,
            name = parsed.name,
            amountText = AmountFormatter.formatForInput(parsed.amount),
            unit = parsed.unit,
            note = parsed.note.orEmpty(),
            canonicalName = parsed.canonicalName,
            needsReview = parsed.confidence != ParseConfidence.HIGH,
            rawLine = parsed.rawLine,
        )
    }
}

/**
 * Zustand des Rezept-Formulars. Wird sowohl beim Bearbeiten als auch beim Import
 * verwendet, damit Korrekturen ueberall gleich funktionieren.
 */
data class RecipeFormState(
    val recipeId: Long = 0L,
    val title: String = "",
    val instructions: String = "",
    val prepTimeText: String = "",
    val photoUri: String? = null,
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val ingredients: List<IngredientDraft> = emptyList(),
    val nextIngredientKey: Long = 1L,
    val createdAt: Long = 0L,
    val lastCookedAt: Long? = null,
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    /**
     * Die vom Anbieter gegliederten Schritte eines importierten Rezepts.
     *
     * Sie werden unveraendert weitergereicht, solange der Zubereitungstext nicht
     * angefasst wird. Wer den Text bearbeitet, bekommt eine neue Aufteilung - eine
     * Gliederung, die zu einem anderen Text gehoert, waere schlechter als keine.
     */
    val importedSteps: List<RecipeStep> = emptyList(),
    val titleError: Boolean = false,
    val prepTimeError: Boolean = false,
) {
    val prepTimeMinutes: Int?
        get() = prepTimeText.trim().toIntOrNull()

    val hasUncertainIngredients: Boolean
        get() = ingredients.any { it.needsReview }

    fun toRecipe(): Recipe = Recipe(
        id = recipeId,
        title = title.trim(),
        instructions = instructions.trim(),
        ingredients = ingredients
            .filter { it.name.isNotBlank() }
            .mapIndexed { index, draft -> draft.toIngredient(index) },
        tags = tags,
        prepTimeMinutes = prepTimeMinutes,
        photoUri = photoUri,
        createdAt = createdAt,
        lastCookedAt = lastCookedAt,
        sourceUrl = sourceUrl,
        sourceName = sourceName,
        steps = importedSteps,
    )

    /** Fuegt eine leere Zutatenzeile an und vergibt dafuer einen stabilen Schluessel. */
    fun withNewIngredient(): RecipeFormState = copy(
        ingredients = ingredients + IngredientDraft(key = nextIngredientKey),
        nextIngredientKey = nextIngredientKey + 1,
    )

    fun withIngredientAt(index: Int, transform: (IngredientDraft) -> IngredientDraft): RecipeFormState {
        if (index !in ingredients.indices) return this
        return copy(
            ingredients = ingredients.toMutableList().apply { this[index] = transform(this[index]) },
        )
    }

    fun withoutIngredientAt(index: Int): RecipeFormState {
        if (index !in ingredients.indices) return this
        return copy(ingredients = ingredients.filterIndexed { i, _ -> i != index })
    }

    fun withTagAdded(tag: String): RecipeFormState {
        val cleaned = tag.trim()
        if (cleaned.isEmpty() || tags.any { it.equals(cleaned, ignoreCase = true) }) {
            return copy(tagInput = "")
        }
        return copy(tags = tags + cleaned, tagInput = "")
    }

    fun withTagRemoved(tag: String): RecipeFormState = copy(tags = tags.filterNot { it == tag })

    companion object {
        /** Baut das Formular aus einem Import-Vorschlag. */
        fun fromParsed(parsed: ParsedRecipe): RecipeFormState = RecipeFormState(
            title = parsed.title,
            instructions = parsed.instructions,
            prepTimeText = parsed.prepTimeMinutes?.toString().orEmpty(),
            tags = parsed.tags,
            ingredients = parsed.ingredients.mapIndexed { index, ingredient ->
                IngredientDraft.from(ingredient, key = index.toLong() + 1)
            },
            nextIngredientKey = parsed.ingredients.size.toLong() + 1,
        )

        fun from(recipe: Recipe): RecipeFormState = RecipeFormState(
            recipeId = recipe.id,
            title = recipe.title,
            instructions = recipe.instructions,
            prepTimeText = recipe.prepTimeMinutes?.toString().orEmpty(),
            photoUri = recipe.photoUri,
            tags = recipe.tags,
            ingredients = recipe.ingredients.mapIndexed { index, ingredient ->
                IngredientDraft.from(ingredient, key = index.toLong() + 1)
            },
            nextIngredientKey = recipe.ingredients.size.toLong() + 1,
            createdAt = recipe.createdAt,
            lastCookedAt = recipe.lastCookedAt,
            sourceUrl = recipe.sourceUrl,
            sourceName = recipe.sourceName,
            importedSteps = recipe.steps,
        )
    }
}

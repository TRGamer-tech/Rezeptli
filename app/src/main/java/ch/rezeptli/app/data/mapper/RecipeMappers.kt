package ch.rezeptli.app.data.mapper

import ch.rezeptli.app.data.local.entity.IngredientEntity
import ch.rezeptli.app.data.local.entity.RecipeEntity
import ch.rezeptli.app.data.local.entity.RecipeSummaryProjection
import ch.rezeptli.app.data.local.entity.RecipeWithDetails
import ch.rezeptli.app.data.local.entity.SwipeResultEntity
import ch.rezeptli.app.data.local.entity.SwipeSessionEntity
import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.model.SwipeDecision
import ch.rezeptli.app.domain.model.SwipeSession

/** Trennzeichen der zusammengefassten Tag-Spalte aus [RecipeSummaryProjection]. */
private const val TAG_SEPARATOR = "|"

fun RecipeWithDetails.toDomain(): Recipe = Recipe(
    id = recipe.id,
    title = recipe.title,
    instructions = recipe.instructions,
    ingredients = ingredients.sortedBy { it.position }.map { it.toDomain() },
    tags = tags.map { it.tag }.sorted(),
    prepTimeMinutes = recipe.prepTimeMinutes,
    photoUri = recipe.photoUri,
    createdAt = recipe.createdAt,
    updatedAt = recipe.updatedAt,
    lastCookedAt = recipe.lastCookedAt,
)

fun IngredientEntity.toDomain(): Ingredient = Ingredient(
    id = id,
    recipeId = recipeId,
    name = name,
    amount = amount,
    unit = unit,
    note = note,
    canonicalName = canonicalName,
    position = position,
)

fun Ingredient.toEntity(recipeId: Long, position: Int): IngredientEntity = IngredientEntity(
    id = id,
    recipeId = recipeId,
    name = name.trim(),
    amount = amount,
    unit = unit,
    note = note?.trim()?.takeIf { it.isNotEmpty() },
    canonicalName = canonicalName?.trim()?.takeIf { it.isNotEmpty() },
    position = position,
)

fun Recipe.toEntity(now: Long): RecipeEntity = RecipeEntity(
    id = id,
    title = title.trim(),
    instructions = instructions.trim(),
    prepTimeMinutes = prepTimeMinutes,
    photoUri = photoUri,
    createdAt = if (createdAt == 0L) now else createdAt,
    updatedAt = now,
    lastCookedAt = lastCookedAt,
)

fun RecipeSummaryProjection.toDomain(): RecipeSummary = RecipeSummary(
    id = id,
    title = title,
    photoUri = photoUri,
    prepTimeMinutes = prepTimeMinutes,
    tags = tags
        ?.split(TAG_SEPARATOR)
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.sorted()
        .orEmpty(),
    lastCookedAt = lastCookedAt,
)

fun SwipeSessionEntity.toDomain(): SwipeSession = SwipeSession(
    id = id,
    startedAt = startedAt,
    finishedAt = finishedAt,
    mode = mode,
)

fun SwipeResultEntity.toDomain(): SwipeDecision = SwipeDecision(
    sessionId = sessionId,
    recipeId = recipeId,
    liked = liked,
    decidedAt = decidedAt,
    participantId = participantId,
)

fun SwipeDecision.toEntity(): SwipeResultEntity = SwipeResultEntity(
    sessionId = sessionId,
    participantId = participantId,
    recipeId = recipeId,
    liked = liked,
    decidedAt = decidedAt,
)

package ch.rezeptli.app.data.repository

import ch.rezeptli.app.data.local.dao.PlanDao
import ch.rezeptli.app.data.local.entity.PlanEntryEntity
import ch.rezeptli.app.data.local.entity.PlanEntryRow
import ch.rezeptli.app.data.mapper.toEntity
import ch.rezeptli.app.di.IoDispatcher
import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.plan.MealLabel
import ch.rezeptli.app.domain.plan.PlanEntry
import ch.rezeptli.app.domain.plan.PlanRepository
import ch.rezeptli.app.domain.plan.PlanWrite
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanRepositoryImpl @Inject constructor(
    private val planDao: PlanDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlanRepository {
    override fun observePlan(): Flow<List<PlanEntry>> =
        planDao.observeRows().map { rows -> rows.map { it.toDomain() }.sortedWith(PLAN_ORDER) }

    override suspend fun currentPlan(): List<PlanEntry> = withContext(ioDispatcher) {
        planDao.rows().map { it.toDomain() }.sortedWith(PLAN_ORDER)
    }

    override suspend fun savePlan(entries: List<PlanWrite>, basket: List<ShoppingItem>) = withContext(ioDispatcher) {
        planDao.replacePlan(entries.map { it.toEntity() }, basket.map { it.toEntity() })
    }

    override suspend fun replaceEntry(entry: PlanWrite, basket: List<ShoppingItem>) = withContext(ioDispatcher) {
        planDao.replaceEntry(entry.toEntity(), basket.map { it.toEntity() })
    }

    override suspend fun markCooked(recipeId: Long, cookedAt: Long?) = withContext(ioDispatcher) {
        planDao.markCooked(recipeId, cookedAt)
    }

    private fun PlanWrite.toEntity() = PlanEntryEntity(
        day = day,
        recipeId = recipeId,
        servings = servings,
        cost = cost,
        label = label.name,
        slot = slot,
        cookedAt = cookedAt,
    )

    private fun PlanEntryRow.toDomain() = PlanEntry(
        day = day,
        slot = slot,
        recipeId = recipeId,
        title = title,
        photoUri = photoUri,
        prepTimeMinutes = prepTimeMinutes,
        servings = servings,
        cost = cost,
        label = MealLabel.entries.firstOrNull { it.name == label } ?: MealLabel.ALLTAG,
        cookedAt = cookedAt,
    )

    private companion object {
        /** Wochentag fuer Wochentag, mittags vor abends. */
        val PLAN_ORDER = compareBy<PlanEntry>({ it.day.ordinal }, { it.slot.ordinal })
    }
}

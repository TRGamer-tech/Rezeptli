package ch.rezeptli.app.di

import ch.rezeptli.app.data.local.AppearanceStore
import ch.rezeptli.app.data.local.DocumentStore
import ch.rezeptli.app.data.local.PhotoStorage
import ch.rezeptli.app.data.local.PlanMemoryStore
import ch.rezeptli.app.data.local.UserProfileStore
import ch.rezeptli.app.data.pairing.ActiveRoundStore
import ch.rezeptli.app.data.pairing.PairingRepositoryImpl
import ch.rezeptli.app.data.repository.PlanRepositoryImpl
import ch.rezeptli.app.data.repository.RecipeRepositoryImpl
import ch.rezeptli.app.data.repository.ShoppingListRepositoryImpl
import ch.rezeptli.app.data.repository.SwipeSessionRepositoryImpl
import ch.rezeptli.app.data.repository.WebRecipeRepositoryImpl
import ch.rezeptli.app.data.translate.MlKitRecipeTranslator
import ch.rezeptli.app.domain.archive.DocumentAccess
import ch.rezeptli.app.domain.archive.PhotoFiles
import ch.rezeptli.app.domain.multiplayer.ActiveRoundRepository
import ch.rezeptli.app.domain.plan.PlanMemory
import ch.rezeptli.app.domain.plan.PlanRepository
import ch.rezeptli.app.domain.repository.PairingRepository
import ch.rezeptli.app.domain.repository.RecipeRepository
import ch.rezeptli.app.domain.repository.ShoppingListRepository
import ch.rezeptli.app.domain.repository.SwipeSessionRepository
import ch.rezeptli.app.domain.repository.UserProfileRepository
import ch.rezeptli.app.domain.repository.WebRecipeRepository
import ch.rezeptli.app.domain.settings.AppearanceRepository
import ch.rezeptli.app.domain.translate.RecipeTranslator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindRecipeRepository(impl: RecipeRepositoryImpl): RecipeRepository

    @Binds
    @Singleton
    abstract fun bindSwipeSessionRepository(impl: SwipeSessionRepositoryImpl): SwipeSessionRepository

    @Binds
    @Singleton
    abstract fun bindShoppingListRepository(impl: ShoppingListRepositoryImpl): ShoppingListRepository

    @Binds
    @Singleton
    abstract fun bindWebRecipeRepository(impl: WebRecipeRepositoryImpl): WebRecipeRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(impl: UserProfileStore): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindPlanRepository(impl: PlanRepositoryImpl): PlanRepository

    @Binds
    @Singleton
    abstract fun bindPlanMemory(impl: PlanMemoryStore): PlanMemory

    @Binds
    @Singleton
    abstract fun bindPhotoFiles(impl: PhotoStorage): PhotoFiles

    @Binds
    @Singleton
    abstract fun bindDocumentAccess(impl: DocumentStore): DocumentAccess

    @Binds
    @Singleton
    abstract fun bindActiveRoundRepository(impl: ActiveRoundStore): ActiveRoundRepository

    @Binds
    @Singleton
    abstract fun bindPairingRepository(impl: PairingRepositoryImpl): PairingRepository

    @Binds
    @Singleton
    abstract fun bindRecipeTranslator(impl: MlKitRecipeTranslator): RecipeTranslator

    @Binds
    @Singleton
    abstract fun bindAppearanceRepository(impl: AppearanceStore): AppearanceRepository
}

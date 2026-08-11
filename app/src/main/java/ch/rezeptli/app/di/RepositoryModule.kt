package ch.rezeptli.app.di

import ch.rezeptli.app.data.local.UserProfileStore
import ch.rezeptli.app.data.repository.RecipeRepositoryImpl
import ch.rezeptli.app.data.repository.ShoppingListRepositoryImpl
import ch.rezeptli.app.data.repository.SwipeSessionRepositoryImpl
import ch.rezeptli.app.data.repository.WebRecipeRepositoryImpl
import ch.rezeptli.app.domain.repository.RecipeRepository
import ch.rezeptli.app.domain.repository.ShoppingListRepository
import ch.rezeptli.app.domain.repository.SwipeSessionRepository
import ch.rezeptli.app.domain.repository.UserProfileRepository
import ch.rezeptli.app.domain.repository.WebRecipeRepository
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
}

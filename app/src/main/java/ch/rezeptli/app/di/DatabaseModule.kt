package ch.rezeptli.app.di

import android.content.Context
import androidx.room.Room
import ch.rezeptli.app.data.local.RezeptliDatabase
import ch.rezeptli.app.data.local.dao.RecipeDao
import ch.rezeptli.app.data.local.dao.SwipeSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): RezeptliDatabase =
        Room.databaseBuilder(context, RezeptliDatabase::class.java, RezeptliDatabase.DATABASE_NAME)
            .addMigrations(*RezeptliDatabase.MIGRATIONS)
            .build()

    @Provides
    fun provideRecipeDao(database: RezeptliDatabase): RecipeDao = database.recipeDao()

    @Provides
    fun provideSwipeSessionDao(database: RezeptliDatabase): SwipeSessionDao = database.swipeSessionDao()
}

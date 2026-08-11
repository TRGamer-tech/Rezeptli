package ch.rezeptli.app.di

import ch.rezeptli.app.data.web.PageFetcher
import ch.rezeptli.app.data.web.PrebuiltIndex
import ch.rezeptli.app.data.web.PrebuiltIndexClient
import ch.rezeptli.app.data.web.RecipeWebClient
import ch.rezeptli.app.data.web.WebIndexCache
import ch.rezeptli.app.data.web.WebIndexStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HttpModule {
    /**
     * Ein schlanker Client ohne Cookies und ohne Zwischenspeicher: Rezeptli ruft
     * oeffentliche Seiten ab und hat keinen Grund, dabei irgendetwas mitzuführen,
     * was einen Nutzer wiedererkennbar macht.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient
        .Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    @Singleton
    abstract fun bindPageFetcher(impl: RecipeWebClient): PageFetcher

    @Binds
    @Singleton
    abstract fun bindWebIndexStore(impl: WebIndexCache): WebIndexStore

    @Binds
    @Singleton
    abstract fun bindPrebuiltIndex(impl: PrebuiltIndexClient): PrebuiltIndex
}

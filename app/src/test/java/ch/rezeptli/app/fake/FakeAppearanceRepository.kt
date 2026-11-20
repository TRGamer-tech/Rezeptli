package ch.rezeptli.app.fake

import ch.rezeptli.app.domain.settings.AppearanceRepository
import ch.rezeptli.app.domain.settings.ThemeChoice
import kotlinx.coroutines.flow.MutableStateFlow

/** Haelt im Speicher, wie die App aussieht. */
class FakeAppearanceRepository(
    theme: ThemeChoice = ThemeChoice.SYSTEM,
    reduceMotion: Boolean = false,
) : AppearanceRepository {
    override val reduceMotion = MutableStateFlow(reduceMotion)

    override suspend fun setReduceMotion(reduce: Boolean) {
        reduceMotion.value = reduce
    }

    override val theme = MutableStateFlow(theme)

    override suspend fun setTheme(choice: ThemeChoice) {
        theme.value = choice
    }
}

package ch.rezeptli.app.fake

import ch.rezeptli.app.domain.multiplayer.ActiveRound
import ch.rezeptli.app.domain.multiplayer.ActiveRoundRepository

/** Haelt die laufende Runde im Speicher. */
class FakeActiveRoundRepository(var round: ActiveRound? = null) : ActiveRoundRepository {
    override suspend fun load(): ActiveRound? = round

    override suspend fun save(round: ActiveRound) {
        this.round = round
    }

    override suspend fun clear() {
        round = null
    }
}

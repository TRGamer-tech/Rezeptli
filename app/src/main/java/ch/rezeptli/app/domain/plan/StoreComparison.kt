package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.profile.StoreChain
import ch.rezeptli.app.domain.profile.UserProfile

/** Was derselbe Einkauf in einem der anderen gewaehlten Laeden kosten wuerde. */
data class StoreEstimate(
    val store: StoreChain,
    val cost: Double,
)

/**
 * Rechnet die Kosten des Hauptladens auf die anderen gewaehlten Laeden um.
 *
 * Gerechnet wird immer mit dem Hauptladen - dem ersten der Auswahl. Die anderen stehen nur
 * zum Vergleich daneben. Die Laeden unterscheiden sich in der App allein durch ihr
 * Preisniveau; der Vergleich ist deshalb eine einfache Verhaeltnisrechnung und genauso grob
 * wie die Preise selbst.
 */
object StoreComparison {
    /** [cost] ist der Einkauf im Hauptladen. Ohne Hauptladen oder ohne Einkauf gibt es nichts zu vergleichen. */
    fun of(profile: UserProfile, cost: Double): List<StoreEstimate> {
        val main = profile.store ?: return emptyList()
        if (cost <= 0.0) return emptyList()
        return profile.stores.drop(1).map { other ->
            StoreEstimate(store = other, cost = cost * other.priceMultiplier / main.priceMultiplier)
        }
    }
}

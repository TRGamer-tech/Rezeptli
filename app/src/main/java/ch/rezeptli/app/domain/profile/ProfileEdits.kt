package ch.rezeptli.app.domain.profile

import ch.rezeptli.app.domain.model.IngredientNameNormalizer

/**
 * Die Aenderungen am Profil, die Einrichtung und Vorlieben gemeinsam brauchen.
 *
 * Sie stehen hier statt in zwei ViewModels, damit "hoechstens drei Stimmungen", "eine
 * Ernaehrungsform, nicht zwei" und "nur Laeden aus dem eigenen Land" an genau einer Stelle
 * gelten - und getestet sind.
 */
object ProfileEdits {
    fun name(profile: UserProfile, name: String): UserProfile = profile.copy(firstName = name.take(MAX_NAME_LENGTH))

    /**
     * Setzt das Land; ein zweiter Tipp auf dasselbe Land hebt die Auswahl wieder auf.
     *
     * Mit dem Land wechseln die Laeden: Ein gewaehlter Laden, den es dort nicht gibt, faellt
     * heraus - sonst wuerde weiter mit Migros gerechnet, obwohl jemand in Wien einkauft.
     *
     * Wechselt dabei die Waehrung, gilt wieder das uebliche Budget des Landes: 150 Franken
     * sind nicht 150 Euro, und stillschweigend umrechnen waere geraten.
     */
    fun country(profile: UserProfile, country: Country): UserProfile {
        val chosen = if (profile.country == country) null else country
        val market = Market.of(chosen)
        return profile.copy(
            country = chosen,
            stores = profile.stores.filter { it in market.stores },
            weeklyBudget = profile.weeklyBudget.takeIf { market.currency == profile.market.currency },
        )
    }

    fun toggleCuisine(profile: UserProfile, cuisine: Cuisine): UserProfile =
        profile.copy(cuisines = profile.cuisines.toggle(cuisine))

    /**
     * Waehlt einen Laden an oder ab. Die Reihenfolge der Auswahl zaehlt: Der erste ist der
     * Hauptladen. Ein vierter wird nicht aufgenommen.
     */
    fun toggleStore(profile: UserProfile, store: StoreChain): UserProfile = when {
        store in profile.stores -> profile.copy(stores = profile.stores - store)
        store !in profile.market.stores -> profile
        profile.stores.size >= UserProfile.MAX_STORES -> profile
        else -> profile.copy(stores = profile.stores + store)
    }

    /** Macht einen schon gewaehlten Laden zum Hauptladen, ohne die anderen abzuwaehlen. */
    fun mainStore(profile: UserProfile, store: StoreChain): UserProfile =
        if (store !in profile.stores) profile else profile.copy(stores = listOf(store) + (profile.stores - store))

    fun persons(profile: UserProfile, delta: Int): UserProfile =
        profile.copy(householdSize = (profile.persons + delta).coerceIn(1, UserProfile.MAX_HOUSEHOLD_SIZE))

    /**
     * Schaltet einen Kochtag ein oder aus. Ausgangspunkt sind die Tage, die tatsaechlich
     * gelten - ohne eigene Auswahl also Montag bis Freitag, genau wie angezeigt. Der letzte
     * Tag laesst sich nicht abwaehlen: Ein Plan ohne Kochtag waere keiner.
     */
    fun toggleDay(profile: UserProfile, day: Weekday): UserProfile {
        val current = profile.days.toSet()
        val next = if (day in current) current - day else current + day
        return if (next.isEmpty()) profile else profile.copy(cookDays = next)
    }

    /**
     * Schaltet Mittag- oder Abendessen ein oder aus. Mindestens eine Mahlzeit bleibt - ein
     * Plan ohne Mahlzeiten waere keiner.
     */
    fun toggleSlot(profile: UserProfile, slot: MealSlot): UserProfile {
        val current = profile.slots.toSet()
        val next = if (slot in current) current - slot else current + slot
        return if (next.isEmpty()) profile else profile.copy(mealSlots = next)
    }

    fun budget(profile: UserProfile, value: Int): UserProfile =
        profile.copy(weeklyBudget = value.coerceIn(UserProfile.MIN_BUDGET, UserProfile.MAX_BUDGET))

    /** Eine vierte Stimmung wird nicht aufgenommen - die Auswahl soll etwas bedeuten. */
    fun toggleMood(profile: UserProfile, mood: Mood): UserProfile = when {
        mood in profile.moods -> profile.copy(moods = profile.moods - mood)
        profile.moods.size >= UserProfile.MAX_MOODS -> profile
        else -> profile.copy(moods = profile.moods + mood)
    }

    /**
     * Setzt die Ernaehrungsform. `null` heisst "keine besondere".
     *
     * Vegan, vegetarisch und pescetarisch schliessen sich aus; "kohlenhydratarm" ist
     * unabhaengig davon und bleibt erhalten.
     */
    fun mainDiet(profile: UserProfile, diet: Diet?): UserProfile {
        val kept = profile.diets - MAIN_DIETS
        return profile.copy(diets = if (diet == null) kept else kept + diet)
    }

    fun toggleDiet(profile: UserProfile, diet: Diet): UserProfile =
        profile.copy(diets = profile.diets.toggle(diet))

    fun toggleIntolerance(profile: UserProfile, intolerance: Intolerance): UserProfile =
        profile.copy(intolerances = profile.intolerances.toggle(intolerance))

    fun toggleDislike(profile: UserProfile, dislike: Dislike): UserProfile =
        profile.copy(dislikes = profile.dislikes.toggle(dislike))

    /**
     * Nimmt ein eigenes Stichwort auf, etwa "Rosinen".
     *
     * Verglichen wird ohne Gross- und Kleinschreibung und ohne Umlaute; was schon dasteht,
     * kommt kein zweites Mal dazu. Ein Stichwort unter drei Buchstaben wuerde zu viel
     * treffen ("ei" steckt in "Reis") und wird nicht angenommen.
     */
    fun addCustomDislike(profile: UserProfile, text: String): UserProfile {
        val word = text.trim().take(UserProfile.MAX_DISLIKE_LENGTH)
        val key = IngredientNameNormalizer.normalize(word)
        val known = profile.customDislikes.map(IngredientNameNormalizer::normalize)
        return when {
            key.length < MIN_DISLIKE_LENGTH -> profile
            key in known -> profile
            profile.customDislikes.size >= UserProfile.MAX_CUSTOM_DISLIKES -> profile
            else -> profile.copy(customDislikes = profile.customDislikes + word)
        }
    }

    fun removeCustomDislike(profile: UserProfile, text: String): UserProfile =
        profile.copy(customDislikes = profile.customDislikes - text)

    fun toggleAppliance(profile: UserProfile, appliance: Appliance): UserProfile =
        profile.copy(appliances = profile.appliances.toggle(appliance))

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

    val MAIN_DIETS: Set<Diet> = setOf(Diet.VEGAN, Diet.VEGETARISCH, Diet.PESCETARISCH)

    const val MIN_DISLIKE_LENGTH = 3
    private const val MAX_NAME_LENGTH = 40
}

/** Die gewaehlte Hauptform der Ernaehrung, falls eine gesetzt ist. */
val UserProfile.mainDiet: Diet?
    get() = listOf(Diet.VEGAN, Diet.VEGETARISCH, Diet.PESCETARISCH).firstOrNull { it in diets }

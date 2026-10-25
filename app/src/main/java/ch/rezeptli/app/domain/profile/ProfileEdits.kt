package ch.rezeptli.app.domain.profile

/**
 * Die Aenderungen am Profil, die Einrichtung und Vorlieben gemeinsam brauchen.
 *
 * Sie stehen hier statt in zwei ViewModels, damit "hoechstens drei Stimmungen" und
 * "eine Ernaehrungsform, nicht zwei" an genau einer Stelle gelten - und getestet sind.
 */
object ProfileEdits {
    /** Eine vierstellige Postleitzahl heisst Schweiz - aber nur, wenn noch kein Land gewaehlt wurde. */
    fun zip(profile: UserProfile, text: String): UserProfile {
        val digits = text.filter { it.isDigit() }.take(MAX_ZIP_LENGTH)
        val inferred = if (profile.country == null && digits.length == SWISS_ZIP_LENGTH) Country.SCHWEIZ else null
        return profile.copy(zip = digits, country = profile.country ?: inferred)
    }

    fun store(profile: UserProfile, store: StoreChain): UserProfile =
        profile.copy(store = if (profile.store == store) null else store)

    fun persons(profile: UserProfile, delta: Int): UserProfile =
        profile.copy(householdSize = (profile.persons + delta).coerceIn(1, UserProfile.MAX_HOUSEHOLD_SIZE))

    fun toggleDay(profile: UserProfile, day: Weekday): UserProfile =
        profile.copy(cookDays = profile.cookDays.toggle(day))

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
        val exclusive = setOf(Diet.VEGAN, Diet.VEGETARISCH, Diet.PESCETARISCH)
        val kept = profile.diets - exclusive
        return profile.copy(diets = if (diet == null) kept else kept + diet)
    }

    fun toggleDiet(profile: UserProfile, diet: Diet): UserProfile =
        profile.copy(diets = profile.diets.toggle(diet))

    fun toggleIntolerance(profile: UserProfile, intolerance: Intolerance): UserProfile =
        profile.copy(intolerances = profile.intolerances.toggle(intolerance))

    fun toggleAppliance(profile: UserProfile, appliance: Appliance): UserProfile =
        profile.copy(appliances = profile.appliances.toggle(appliance))

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

    private const val MAX_ZIP_LENGTH = 5
    private const val SWISS_ZIP_LENGTH = 4
}

/** Die gewaehlte Hauptform der Ernaehrung, falls eine gesetzt ist. */
val UserProfile.mainDiet: Diet?
    get() = listOf(Diet.VEGAN, Diet.VEGETARISCH, Diet.PESCETARISCH).firstOrNull { it in diets }

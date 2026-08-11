package ch.rezeptli.app.data.local

import androidx.room.TypeConverter
import ch.rezeptli.app.domain.model.IngredientCategory
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.SwipeMode

/**
 * Enums werden als Name gespeichert, nicht als Ordinalwert. Damit bleibt die Datenbank
 * lesbar und das Umsortieren oder Ergaenzen von Enum-Werten bricht keine Bestandsdaten.
 */
class Converters {
    @TypeConverter
    fun toIngredientUnit(value: String?): IngredientUnit =
        value?.let { name -> IngredientUnit.entries.firstOrNull { it.name == name } } ?: IngredientUnit.NONE

    @TypeConverter
    fun fromIngredientUnit(unit: IngredientUnit): String = unit.name

    @TypeConverter
    fun toIngredientCategory(value: String?): IngredientCategory =
        value?.let { name -> IngredientCategory.entries.firstOrNull { it.name == name } }
            ?: IngredientCategory.SONSTIGES

    @TypeConverter
    fun fromIngredientCategory(category: IngredientCategory): String = category.name

    @TypeConverter
    fun toSwipeMode(value: String?): SwipeMode =
        value?.let { name -> SwipeMode.entries.firstOrNull { it.name == name } } ?: SwipeMode.SOLO

    @TypeConverter
    fun fromSwipeMode(mode: SwipeMode): String = mode.name
}

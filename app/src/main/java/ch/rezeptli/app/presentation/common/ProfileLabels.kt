package ch.rezeptli.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Intolerance

/*
 * Beschriftungen der Profil-Aufzaehlungen.
 *
 * Sie stehen hier und nicht am Modell, damit die Domaenenschicht ohne Android
 * auskommt und die Texte uebersetzbar bleiben.
 */

@Composable
fun Country.label(): String = stringResource(
    when (this) {
        Country.SCHWEIZ -> R.string.land_schweiz
        Country.DEUTSCHLAND -> R.string.land_deutschland
        Country.OESTERREICH -> R.string.land_oesterreich
        Country.FRANKREICH -> R.string.land_frankreich
        Country.ITALIEN -> R.string.land_italien
        Country.ANDERSWO -> R.string.land_anderswo
    },
)

@Composable
fun Cuisine.label(): String = stringResource(
    when (this) {
        Cuisine.SCHWEIZER_KLASSIKER -> R.string.kueche_schweizer_klassiker
        Cuisine.DEUTSCH_OESTERREICHISCH -> R.string.kueche_deutsch_oesterreichisch
        Cuisine.ITALIENISCH -> R.string.kueche_italienisch
        Cuisine.FRANZOESISCH -> R.string.kueche_franzoesisch
        Cuisine.MEDITERRAN -> R.string.kueche_mediterran
        Cuisine.ASIATISCH -> R.string.kueche_asiatisch
        Cuisine.ORIENTALISCH -> R.string.kueche_orientalisch
        Cuisine.AMERIKANISCH -> R.string.kueche_amerikanisch
        Cuisine.LATEINAMERIKANISCH -> R.string.kueche_lateinamerikanisch
    },
)

@Composable
fun Diet.label(): String = stringResource(
    when (this) {
        Diet.VEGETARISCH -> R.string.ernaehrung_vegetarisch
        Diet.VEGAN -> R.string.ernaehrung_vegan
        Diet.PESCETARISCH -> R.string.ernaehrung_pescetarisch
        Diet.KOHLENHYDRATARM -> R.string.ernaehrung_kohlenhydratarm
    },
)

@Composable
fun Intolerance.label(): String = stringResource(
    when (this) {
        Intolerance.GLUTEN -> R.string.unvertraeglichkeit_gluten
        Intolerance.LAKTOSE -> R.string.unvertraeglichkeit_laktose
        Intolerance.NUESSE -> R.string.unvertraeglichkeit_nuesse
        Intolerance.EIER -> R.string.unvertraeglichkeit_eier
        Intolerance.SOJA -> R.string.unvertraeglichkeit_soja
        Intolerance.MEERESFRUECHTE -> R.string.unvertraeglichkeit_meeresfruechte
    },
)

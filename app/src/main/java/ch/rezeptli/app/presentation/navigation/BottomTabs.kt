package ch.rezeptli.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import ch.rezeptli.app.R

/**
 * Die fünf Orte, die immer erreichbar sind.
 *
 * Der Plan steht vorn: Die App beantwortet die Frage "was koche ich diese Woche". Wischen
 * ist der Weg zu neuen Ideen - allein oder zu zweit -, Rezepte die eigene Sammlung,
 * Vorlieben und Einstellungen die zwei Orte zum Nachjustieren.
 */
enum class TabZiel(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int,
) {
    PLAN(Destinations.PLAN, Icons.Filled.CalendarMonth, R.string.tab_plan),
    WISCHEN(Destinations.DECK, Icons.Filled.Style, R.string.tab_wischen),
    REZEPTE(Destinations.RECIPE_LIST, Icons.AutoMirrored.Filled.MenuBook, R.string.tab_rezepte),
    VORLIEBEN(Destinations.PREFERENCES, Icons.Filled.Tune, R.string.tab_vorlieben),
    EINSTELLUNGEN(Destinations.SETTINGS, Icons.Filled.Settings, R.string.tab_einstellungen),
}

/** Auf welchen Bildschirmen die Leiste steht - ueberall sonst waere sie im Weg. */
fun zeigtLeiste(route: String?): Boolean = TabZiel.entries.any { it.route == route }

@Composable
fun RezeptliBottomBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val aktuell = backStackEntry?.destination?.route

    NavigationBar(modifier = modifier) {
        TabZiel.entries.forEach { ziel ->
            NavigationBarItem(
                selected = aktuell == ziel.route,
                onClick = {
                    if (aktuell == ziel.route) return@NavigationBarItem
                    navController.navigate(ziel.route) {
                        // Ein Tab-Wechsel soll keinen Stapel aufbauen: Zurueck fuehrt
                        // vom Tab immer auf den Start, nicht durch die Wechselhistorie.
                        popUpTo(Destinations.PLAN) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(imageVector = ziel.icon, contentDescription = null) },
                label = { Text(stringResource(ziel.labelRes)) },
            )
        }
    }
}

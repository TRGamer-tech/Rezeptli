package ch.rezeptli.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Style
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
 * Die vier Orte, die immer erreichbar sind.
 *
 * Wischen steht vorn: Die App beantwortet die Frage "was koche ich heute", und darauf
 * ist Wischen die Antwort. Suchen bleibt daneben - wer schon weiss, was er will, soll
 * nicht erst durch einen Stapel.
 */
enum class TabZiel(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int,
) {
    WISCHEN(Destinations.DECK, Icons.Filled.Style, R.string.tab_wischen),
    SUCHEN(Destinations.WEB_SEARCH, Icons.Filled.Search, R.string.tab_suchen),
    REZEPTE(Destinations.RECIPE_LIST, Icons.AutoMirrored.Filled.MenuBook, R.string.tab_rezepte),
    EINKAUF(Destinations.SHOPPING_LIST, Icons.Filled.ShoppingCart, R.string.tab_einkauf),
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
                        popUpTo(Destinations.DECK) { saveState = true }
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

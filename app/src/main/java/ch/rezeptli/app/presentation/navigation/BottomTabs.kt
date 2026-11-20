package ch.rezeptli.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import ch.rezeptli.app.R
import ch.rezeptli.app.presentation.common.components.IconMotion
import ch.rezeptli.app.presentation.common.components.MotionIcon

/**
 * Die vier Orte, die immer erreichbar sind.
 *
 * Der Plan steht vorn: Die App beantwortet die Frage "was koche ich diese Woche". Wischen
 * ist der Weg zu neuen Ideen - allein oder zu zweit -, Rezepte die eigene Sammlung, und
 * unter Vorlieben steht alles, was die App ueber einen weiss. Einen eigenen Tab
 * "Einstellungen" gibt es nicht mehr: Das Wort brach in der Leiste um, und was dort stand,
 * waren ohnehin Vorlieben.
 *
 * Jedes Symbol hat seine eigene kleine Bewegung, wenn man den Tab waehlt: Der Kalender
 * blaettert um, die Karten wackeln, das Buch huepft, die Regler schieben.
 */
enum class TabZiel(
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val motion: IconMotion,
    val labelRes: Int,
) {
    PLAN(
        Destinations.PLAN,
        Icons.Outlined.CalendarMonth,
        Icons.Filled.CalendarMonth,
        IconMotion.FLIP,
        R.string.tab_plan,
    ),
    WISCHEN(Destinations.DECK, Icons.Outlined.Style, Icons.Filled.Style, IconMotion.WIGGLE, R.string.tab_wischen),
    REZEPTE(
        Destinations.RECIPE_LIST,
        Icons.AutoMirrored.Outlined.MenuBook,
        Icons.AutoMirrored.Filled.MenuBook,
        IconMotion.HOP,
        R.string.tab_rezepte,
    ),
    VORLIEBEN(
        Destinations.PREFERENCES,
        Icons.Outlined.Tune,
        Icons.Filled.Tune,
        IconMotion.SLIDE,
        R.string.tab_vorlieben,
    ),
}

/** Auf welchen Bildschirmen die Leiste steht - ueberall sonst waere sie im Weg. */
fun zeigtLeiste(route: String?): Boolean = TabZiel.entries.any { it.route == route }

@Composable
fun RezeptliBottomBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val aktuell = backStackEntry?.destination?.route

    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
    ) {
        TabZiel.entries.forEach { ziel ->
            val ausgewaehlt = aktuell == ziel.route
            NavigationBarItem(
                selected = ausgewaehlt,
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
                icon = {
                    // Der Umriss wird beim Waehlen zur gefuellten Form - und bewegt sich dabei.
                    // Beim Abwaehlen bleibt er ruhig: null loest keine Bewegung aus.
                    MotionIcon(
                        imageVector = if (ausgewaehlt) ziel.selectedIcon else ziel.icon,
                        contentDescription = null,
                        motion = ziel.motion,
                        trigger = if (ausgewaehlt) true else null,
                    )
                },
                label = {
                    // Eine Zeile, immer: Ein umbrechendes Etikett schiebt die ganze Leiste hoeher.
                    Text(
                        text = stringResource(ziel.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

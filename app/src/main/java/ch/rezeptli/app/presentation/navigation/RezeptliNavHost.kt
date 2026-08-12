package ch.rezeptli.app.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ch.rezeptli.app.presentation.cooking.CookingRoute
import ch.rezeptli.app.presentation.deck.DeckRoute
import ch.rezeptli.app.presentation.multiplayer.MultiplayerRoute
import ch.rezeptli.app.presentation.onboarding.OnboardingRoute
import ch.rezeptli.app.presentation.recipedetail.RecipeDetailRoute
import ch.rezeptli.app.presentation.recipeedit.RecipeEditRoute
import ch.rezeptli.app.presentation.recipeimport.RecipeImportRoute
import ch.rezeptli.app.presentation.recipelist.RecipeListRoute
import ch.rezeptli.app.presentation.results.ResultsRoute
import ch.rezeptli.app.presentation.settings.SettingsRoute
import ch.rezeptli.app.presentation.shoppinglist.ShoppingListRoute
import ch.rezeptli.app.presentation.swipe.SwipeRoute
import ch.rezeptli.app.presentation.swipe.SwipeSetupRoute
import ch.rezeptli.app.presentation.websearch.WebSearchRoute

/** Der Navigationsgraph der App. */
@Composable
fun RezeptliNavHost(
    sharedUrl: String? = null,
    startDestination: String = Destinations.DECK,
    navController: NavHostController = rememberNavController(),
) {
    // Ein geteilter Link fuehrt einmalig direkt in den Import.
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            navController.navigate(Destinations.recipeImport(sharedUrl))
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()

    Scaffold(
        bottomBar = {
            // Die Leiste steht nur auf den vier Hauptbildschirmen. Beim Wischen einer
            // gemeinsamen Runde oder im Kochmodus waere sie ein Ausrutscher zu viel.
            if (zeigtLeiste(backStackEntry?.destination?.route)) {
                RezeptliBottomBar(navController = navController)
            }
        },
    ) { leistenAbstand ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(leistenAbstand),
        ) {
            composable(Destinations.ONBOARDING) {
                OnboardingRoute(
                    onFinished = {
                        navController.navigate(Destinations.DECK) {
                            // Das Onboarding soll nicht ueber "Zurueck" wiederkehren.
                            popUpTo(Destinations.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }

            composable(Destinations.DECK) {
                DeckRoute(
                    onOpenTogether = { navController.navigate(Destinations.MULTIPLAYER) },
                )
            }

            composable(Destinations.SETTINGS) {
                SettingsRoute(onBack = { navController.popBackStack() })
            }

            composable(Destinations.RECIPE_LIST) {
                RecipeListRoute(
                    onRecipeClick = { recipeId ->
                        navController.navigate(Destinations.recipeDetail(recipeId))
                    },
                    onCreateRecipe = { navController.navigate(Destinations.recipeEdit()) },
                    onImportRecipe = { navController.navigate(Destinations.recipeImport()) },
                    onSearchWeb = { navController.navigate(Destinations.WEB_SEARCH) },
                    onStartSwipe = { navController.navigate(Destinations.SWIPE_SETUP) },
                    onOpenShoppingList = { navController.navigate(Destinations.SHOPPING_LIST) },
                    onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                )
            }

            composable(
                route = Destinations.RECIPE_DETAIL,
                arguments = listOf(navArgument(Destinations.ARG_RECIPE_ID) { type = NavType.LongType }),
            ) {
                RecipeDetailRoute(
                    onBack = { navController.popBackStack() },
                    onEdit = { recipeId -> navController.navigate(Destinations.recipeEdit(recipeId)) },
                    onStartCooking = { recipeId -> navController.navigate(Destinations.cooking(recipeId)) },
                )
            }

            composable(
                route = Destinations.COOKING,
                arguments = listOf(navArgument(Destinations.ARG_RECIPE_ID) { type = NavType.LongType }),
            ) {
                CookingRoute(onClose = { navController.popBackStack() })
            }

            composable(
                route = Destinations.RECIPE_EDIT,
                arguments = listOf(
                    navArgument(Destinations.ARG_RECIPE_ID) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { backStackEntry ->
                val recipeId = backStackEntry.arguments?.getLong(Destinations.ARG_RECIPE_ID) ?: 0L
                RecipeEditRoute(
                    onDone = { savedId ->
                        if (recipeId == 0L) {
                            // Neu angelegt: direkt zum frisch gespeicherten Rezept.
                            navController.navigate(Destinations.recipeDetail(savedId)) {
                                popUpTo(Destinations.RECIPE_LIST)
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onCancel = { navController.popBackStack() },
                )
            }

            composable(
                route = Destinations.RECIPE_IMPORT,
                arguments = listOf(
                    navArgument(Destinations.ARG_URL) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) {
                RecipeImportRoute(
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        navController.navigate(Destinations.RECIPE_LIST) {
                            popUpTo(Destinations.RECIPE_LIST) { inclusive = true }
                        }
                    },
                )
            }

            composable(Destinations.WEB_SEARCH) {
                WebSearchRoute(
                    onBack = { navController.popBackStack() },
                    onOpenResult = { url -> navController.navigate(Destinations.recipeImport(url)) },
                )
            }

            composable(Destinations.SWIPE_SETUP) {
                SwipeSetupRoute(
                    onBack = { navController.popBackStack() },
                    onStart = { filter ->
                        navController.navigate(Destinations.swipe(filter)) {
                            popUpTo(Destinations.SWIPE_SETUP) { inclusive = true }
                        }
                    },
                    // Der gemeinsame Modus zieht seinen eigenen Vorschlag aus dem
                    // Verzeichnis - der hier gewaehlte Filter fuer die eigene Sammlung
                    // gilt dafuer nicht.
                    onStartTogether = { navController.navigate(Destinations.MULTIPLAYER) },
                )
            }

            composable(Destinations.MULTIPLAYER) {
                MultiplayerRoute(onBack = { navController.popBackStack() })
            }

            composable(
                route = Destinations.SWIPE,
                arguments = listOf(
                    navArgument(Destinations.ARG_QUERY) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Destinations.ARG_TAGS) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Destinations.ARG_MAX_PREP_TIME) {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                ),
            ) {
                SwipeRoute(
                    onBack = { navController.popBackStack() },
                    onShowResults = { sessionId ->
                        navController.navigate(Destinations.results(sessionId)) {
                            popUpTo(Destinations.RECIPE_LIST)
                        }
                    },
                )
            }

            composable(
                route = Destinations.RESULTS,
                arguments = listOf(navArgument(Destinations.ARG_SESSION_ID) { type = NavType.LongType }),
            ) {
                ResultsRoute(
                    onBackToRecipes = {
                        navController.popBackStack(Destinations.RECIPE_LIST, inclusive = false)
                    },
                    onRecipeClick = { recipeId ->
                        navController.navigate(Destinations.recipeDetail(recipeId))
                    },
                    onNewSession = {
                        navController.navigate(Destinations.SWIPE_SETUP) {
                            popUpTo(Destinations.RECIPE_LIST)
                        }
                    },
                    onOpenShoppingList = { navController.navigate(Destinations.SHOPPING_LIST) },
                )
            }

            composable(Destinations.SHOPPING_LIST) {
                ShoppingListRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}

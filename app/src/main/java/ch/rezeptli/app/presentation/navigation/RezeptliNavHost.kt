package ch.rezeptli.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ch.rezeptli.app.presentation.recipedetail.RecipeDetailRoute
import ch.rezeptli.app.presentation.recipeedit.RecipeEditRoute
import ch.rezeptli.app.presentation.recipeimport.RecipeImportRoute
import ch.rezeptli.app.presentation.recipelist.RecipeListRoute
import ch.rezeptli.app.presentation.results.ResultsRoute
import ch.rezeptli.app.presentation.shoppinglist.ShoppingListRoute
import ch.rezeptli.app.presentation.swipe.SwipeRoute
import ch.rezeptli.app.presentation.swipe.SwipeSetupRoute
import ch.rezeptli.app.presentation.websearch.WebSearchRoute

/** Der Navigationsgraph der App. */
@Composable
fun RezeptliNavHost(
    sharedUrl: String? = null,
    navController: NavHostController = rememberNavController(),
) {
    // Ein geteilter Link fuehrt einmalig direkt in den Import.
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            navController.navigate(Destinations.recipeImport(sharedUrl))
        }
    }

    NavHost(
        navController = navController,
        startDestination = Destinations.RECIPE_LIST,
    ) {
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
            )
        }

        composable(
            route = Destinations.RECIPE_DETAIL,
            arguments = listOf(navArgument(Destinations.ARG_RECIPE_ID) { type = NavType.LongType }),
        ) {
            RecipeDetailRoute(
                onBack = { navController.popBackStack() },
                onEdit = { recipeId -> navController.navigate(Destinations.recipeEdit(recipeId)) },
            )
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
                onSaved = { recipeId ->
                    navController.navigate(Destinations.recipeDetail(recipeId)) {
                        popUpTo(Destinations.RECIPE_LIST)
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
            )
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

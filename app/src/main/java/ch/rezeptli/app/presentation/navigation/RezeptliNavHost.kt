package ch.rezeptli.app.presentation.navigation

import androidx.compose.runtime.Composable
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
import ch.rezeptli.app.presentation.swipe.SwipeRoute
import ch.rezeptli.app.presentation.swipe.SwipeSetupRoute

/** Der Navigationsgraph der App. */
@Composable
fun RezeptliNavHost(
    navController: NavHostController = rememberNavController(),
) {
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
                onImportRecipe = { navController.navigate(Destinations.RECIPE_IMPORT) },
                onStartSwipe = { navController.navigate(Destinations.SWIPE_SETUP) },
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

        composable(Destinations.RECIPE_IMPORT) {
            RecipeImportRoute(
                onBack = { navController.popBackStack() },
                onSaved = { recipeId ->
                    navController.navigate(Destinations.recipeDetail(recipeId)) {
                        popUpTo(Destinations.RECIPE_LIST)
                    }
                },
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
            )
        }
    }
}

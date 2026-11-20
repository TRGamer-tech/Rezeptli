package ch.rezeptli.app.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ch.rezeptli.app.presentation.common.components.aboveBarsAndKeyboard
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.cooking.CookingRoute
import ch.rezeptli.app.presentation.deck.DeckRoute
import ch.rezeptli.app.presentation.multiplayer.MultiplayerRoute
import ch.rezeptli.app.presentation.plan.PlanBuildRoute
import ch.rezeptli.app.presentation.plan.PlanRoute
import ch.rezeptli.app.presentation.plan.PlanSetupRoute
import ch.rezeptli.app.presentation.plan.PreferencesRoute
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
    startDestination: String = Destinations.PLAN,
    navController: NavHostController = rememberNavController(),
    shell: NavShellViewModel = hiltViewModel(),
) {
    // Ein geteilter Link fuehrt einmalig direkt in den Import.
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            navController.navigate(Destinations.recipeImport(sharedUrl))
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val openShoppingItems by shell.openShoppingItems.collectAsStateWithLifecycle()
    val motion = RezeptliTheme.motion

    Scaffold(
        bottomBar = {
            // Die Leiste steht nur auf den Hauptbildschirmen. Beim Wischen einer
            // gemeinsamen Runde oder im Kochmodus waere sie ein Ausrutscher zu viel.
            // Sie gleitet hinaus und wieder herein, statt einfach zu verschwinden.
            AnimatedVisibility(
                visible = zeigtLeiste(backStackEntry?.destination?.route),
                enter = if (motion.reduced) {
                    fadeIn(motion.fade())
                } else {
                    slideInVertically(motion.offsetSpring()) { it } + fadeIn(motion.fade())
                },
                exit = if (motion.reduced) {
                    fadeOut(motion.fade())
                } else {
                    slideOutVertically(motion.offsetSpring()) { it } + fadeOut(motion.fade())
                },
            ) {
                RezeptliBottomBar(navController = navController, openShoppingItems = openShoppingItems)
            }
        },
    ) { leistenAbstand ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            // Ueber der Leiste - und ueber der Tastatur, damit sie kein Eingabefeld verdeckt.
            modifier = Modifier.aboveBarsAndKeyboard(leistenAbstand),
            // Vorwaerts schiebt sich der neue Bildschirm von der Seite herein, zurueck
            // gleitet er wieder hinaus. Zwischen Tabs gibt es keine Richtung - dort
            // blendet der Inhalt nur weich ueber.
            enterTransition = {
                when {
                    motion.reduced -> fadeIn(motion.fade())
                    isTabSwitch() -> fadeIn(tween(TAB_FADE_MS, delayMillis = TAB_FADE_DELAY_MS)) +
                        scaleIn(motion.snappy(), initialScale = TAB_SCALE)
                    else -> slideIntoContainer(SlideDirection.Start, motion.offsetSpring()) { it / 4 } +
                        fadeIn(tween(SCREEN_FADE_MS))
                }
            },
            exitTransition = {
                when {
                    motion.reduced -> fadeOut(motion.fade())
                    isTabSwitch() -> fadeOut(tween(TAB_FADE_DELAY_MS))
                    else -> slideOutOfContainer(SlideDirection.Start, motion.offsetSpring()) { it / 8 } +
                        fadeOut(tween(SCREEN_FADE_MS))
                }
            },
            popEnterTransition = {
                when {
                    motion.reduced -> fadeIn(motion.fade())
                    isTabSwitch() -> fadeIn(tween(TAB_FADE_MS, delayMillis = TAB_FADE_DELAY_MS)) +
                        scaleIn(motion.snappy(), initialScale = TAB_SCALE)
                    else -> slideIntoContainer(SlideDirection.End, motion.offsetSpring()) { it / 8 } +
                        fadeIn(tween(SCREEN_FADE_MS))
                }
            },
            popExitTransition = {
                when {
                    motion.reduced -> fadeOut(motion.fade())
                    isTabSwitch() -> fadeOut(tween(TAB_FADE_DELAY_MS))
                    else -> slideOutOfContainer(SlideDirection.End, motion.offsetSpring()) { it / 4 } +
                        fadeOut(tween(SCREEN_FADE_MS))
                }
            },
        ) {
            composable(Destinations.ONBOARDING) {
                PlanSetupRoute(
                    onFinished = {
                        navController.navigate(Destinations.PLAN_BUILD) {
                            // Die Einrichtung soll nicht ueber "Zurueck" wiederkehren.
                            popUpTo(Destinations.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }

            composable(Destinations.PLAN) {
                PlanRoute(
                    onOpenRecipe = { recipeId -> navController.navigate(Destinations.recipeDetail(recipeId)) },
                    onOpenShoppingList = { navController.openTab(TabZiel.EINKAUF) },
                    onBuildPlan = { navController.navigate(Destinations.PLAN_BUILD) },
                )
            }

            composable(Destinations.PLAN_BUILD) {
                val toPlan: () -> Unit = {
                    navController.navigate(Destinations.PLAN) {
                        popUpTo(Destinations.PLAN_BUILD) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                PlanBuildRoute(
                    onBuilt = toPlan,
                    onSkip = toPlan,
                    onEditPreferences = {
                        navController.navigate(Destinations.PREFERENCES) {
                            popUpTo(Destinations.PLAN_BUILD) { inclusive = true }
                        }
                    },
                )
            }

            composable(Destinations.PREFERENCES) {
                PreferencesRoute(onBuildPlan = { navController.navigate(Destinations.PLAN_BUILD) })
            }

            composable(Destinations.DECK) {
                DeckRoute(
                    onOpenTogether = { navController.navigate(Destinations.MULTIPLAYER) },
                    onLeave = { navController.popBackStack() },
                )
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
                    onOpenShoppingList = { navController.openTab(TabZiel.EINKAUF) },
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
                    onOpenShoppingList = { navController.openTab(TabZiel.EINKAUF) },
                )
            }

            composable(Destinations.SHOPPING_LIST) {
                ShoppingListRoute()
            }
        }
    }
}

/** Ein Wechsel zwischen zwei Tabs der unteren Leiste - kein Vor und Zurueck. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    zeigtLeiste(initialState.destination.route) && zeigtLeiste(targetState.destination.route)

private const val TAB_FADE_MS = 220
private const val TAB_FADE_DELAY_MS = 90
private const val TAB_SCALE = 0.96f
private const val SCREEN_FADE_MS = 200

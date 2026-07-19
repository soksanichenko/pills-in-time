package app.zelgray.pills_in_time.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.notification.OccurrenceRequest
import app.zelgray.pills_in_time.notification.StockRequest
import app.zelgray.pills_in_time.ui.drugs.AddEditDrugScreen
import app.zelgray.pills_in_time.ui.drugs.AddEditPeriodScreen
import app.zelgray.pills_in_time.ui.drugs.AddEditStockScreen
import app.zelgray.pills_in_time.ui.drugs.DrugDetailScreen
import app.zelgray.pills_in_time.ui.drugs.DrugsListScreen
import app.zelgray.pills_in_time.ui.history.AddEditHistoryEntryScreen
import app.zelgray.pills_in_time.ui.history.HistoryScreen
import app.zelgray.pills_in_time.ui.home.HomeScreen
import app.zelgray.pills_in_time.ui.settings.SettingsScreen

private data class TabItem(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val tabItems = listOf(
    TabItem(NavRoutes.HOME, R.string.nav_home, Icons.Filled.Home),
    TabItem(NavRoutes.DRUGS_LIST, R.string.nav_drugs, Icons.Filled.Medication),
    TabItem(NavRoutes.HISTORY, R.string.nav_history, Icons.Filled.History),
    TabItem(NavRoutes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun MedTrackerNavHost(
    navController: NavHostController = rememberNavController(),
    pendingOccurrenceRequest: OccurrenceRequest? = null,
    onPendingOccurrenceConsumed: () -> Unit = {},
    pendingStockRequest: StockRequest? = null,
    onPendingStockConsumed: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || currentRoute in NavRoutes.TAB_ROUTES

    LaunchedEffect(pendingOccurrenceRequest) {
        if (pendingOccurrenceRequest != null && currentRoute != NavRoutes.HOME) {
            navController.navigate(NavRoutes.HOME) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(pendingStockRequest) {
        if (pendingStockRequest != null) {
            navController.navigate(NavRoutes.editStock(pendingStockRequest.drugId, pendingStockRequest.stockId))
            onPendingStockConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabItems.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(NavRoutes.HOME) {
                HomeScreen(
                    pendingOccurrenceRequest = pendingOccurrenceRequest,
                    onPendingOccurrenceConsumed = onPendingOccurrenceConsumed,
                )
            }

            composable(NavRoutes.DRUGS_LIST) {
                DrugsListScreen(
                    onDrugClick = { drugId -> navController.navigate(NavRoutes.drugDetail(drugId)) },
                    onAddDrugClick = { navController.navigate(NavRoutes.ADD_DRUG) },
                )
            }

            composable(NavRoutes.HISTORY) {
                HistoryScreen(
                    onAddEntryClick = { navController.navigate(NavRoutes.ADD_HISTORY_ENTRY) },
                    onEditEntryClick = { navController.navigate(NavRoutes.editHistoryEntry(it)) },
                )
            }

            composable(NavRoutes.SETTINGS) { SettingsScreen() }

            composable(
                route = NavRoutes.DRUG_DETAIL,
                arguments = listOf(navArgument(NavRoutes.ARG_DRUG_ID) { type = NavType.LongType }),
            ) { backStack ->
                val drugId = backStack.arguments?.getLong(NavRoutes.ARG_DRUG_ID) ?: return@composable
                DrugDetailScreen(
                    drugId = drugId,
                    onBack = { navController.popBackStack() },
                    onEditDrug = { navController.navigate(NavRoutes.editDrug(it)) },
                    onAddStock = { navController.navigate(NavRoutes.addStock(it)) },
                    onEditStock = { did, sid -> navController.navigate(NavRoutes.editStock(did, sid)) },
                    onAddPeriod = { navController.navigate(NavRoutes.addPeriod(it)) },
                    onEditPeriod = { did, sid -> navController.navigate(NavRoutes.editPeriod(did, sid)) },
                    onDeleted = {
                        navController.popBackStack(NavRoutes.DRUGS_LIST, inclusive = false)
                    },
                )
            }

            composable(NavRoutes.ADD_DRUG) {
                AddEditDrugScreen(
                    drugId = null,
                    onBack = { navController.popBackStack() },
                    onSaved = { drugId ->
                        navController.popBackStack()
                        navController.navigate(NavRoutes.drugDetail(drugId))
                    },
                )
            }

            composable(
                route = NavRoutes.EDIT_DRUG,
                arguments = listOf(navArgument(NavRoutes.ARG_DRUG_ID) { type = NavType.LongType }),
            ) { backStack ->
                val drugId = backStack.arguments?.getLong(NavRoutes.ARG_DRUG_ID) ?: return@composable
                AddEditDrugScreen(
                    drugId = drugId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable(
                route = NavRoutes.ADD_STOCK,
                arguments = listOf(navArgument(NavRoutes.ARG_DRUG_ID) { type = NavType.LongType }),
            ) { backStack ->
                val drugId = backStack.arguments?.getLong(NavRoutes.ARG_DRUG_ID) ?: return@composable
                AddEditStockScreen(
                    drugId = drugId,
                    stockId = null,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable(
                route = NavRoutes.EDIT_STOCK,
                arguments = listOf(
                    navArgument(NavRoutes.ARG_DRUG_ID) { type = NavType.LongType },
                    navArgument(NavRoutes.ARG_STOCK_ID) { type = NavType.LongType },
                ),
            ) { backStack ->
                val drugId = backStack.arguments?.getLong(NavRoutes.ARG_DRUG_ID) ?: return@composable
                val stockId = backStack.arguments?.getLong(NavRoutes.ARG_STOCK_ID) ?: return@composable
                AddEditStockScreen(
                    drugId = drugId,
                    stockId = stockId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable(
                route = NavRoutes.ADD_PERIOD,
                arguments = listOf(navArgument(NavRoutes.ARG_DRUG_ID) { type = NavType.LongType }),
            ) {
                AddEditPeriodScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable(
                route = NavRoutes.EDIT_PERIOD,
                arguments = listOf(
                    navArgument(NavRoutes.ARG_DRUG_ID) { type = NavType.LongType },
                    navArgument(NavRoutes.ARG_SCHEDULE_ID) { type = NavType.LongType },
                ),
            ) {
                AddEditPeriodScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable(NavRoutes.ADD_HISTORY_ENTRY) {
                AddEditHistoryEntryScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable(
                route = NavRoutes.EDIT_HISTORY_ENTRY,
                arguments = listOf(navArgument(NavRoutes.ARG_LOG_ID) { type = NavType.LongType }),
            ) {
                AddEditHistoryEntryScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
        }
    }
}

package app.zelgray.pills_in_time.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import app.zelgray.pills_in_time.ui.patients.PatientsScreen
import app.zelgray.pills_in_time.ui.patients.PatientsViewModel
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
            navController.navigate(NavRoutes.drugDetail(pendingStockRequest.drugId))
            onPendingStockConsumed()
        }
    }

    Scaffold(
        topBar = {
            if (showBottomBar) {
                PatientSwitcherBar(onManagePatients = { navController.navigate(NavRoutes.PATIENTS) })
            }
        },
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

            composable(NavRoutes.PATIENTS) {
                PatientsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Slim, always-visible bar (above the per-tab content) showing which patient
 * every tab is currently scoped to and letting the user switch — so it never
 * requires digging into Settings to tell whose data is on screen.
 */
@Composable
private fun PatientSwitcherBar(
    onManagePatients: () -> Unit,
    viewModel: PatientsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    val current = state.patients.find { it.id == state.currentPatientId }

    Surface {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (current != null) {
                    Box(modifier = Modifier.size(16.dp).background(Color(current.color), CircleShape))
                    Text(
                        text = current.name,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.patients.forEach { patient ->
                    DropdownMenuItem(
                        text = { Text(patient.name) },
                        leadingIcon = { Box(modifier = Modifier.size(16.dp).background(Color(patient.color), CircleShape)) },
                        trailingIcon = {
                            if (patient.id == state.currentPatientId) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            viewModel.onSelectPatient(patient.id)
                            expanded = false
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.manage_patients_action)) },
                    onClick = {
                        expanded = false
                        onManagePatients()
                    },
                )
            }
        }
    }
}

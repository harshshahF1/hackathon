package com.hackathon.apprisk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import androidx.core.view.WindowCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hackathon.apprisk.domain.model.PermissionCategory
import com.hackathon.apprisk.ui.AppViewModel
import com.hackathon.apprisk.ui.AppDetailScreen
import com.hackathon.apprisk.ui.AppsScreen
import com.hackathon.apprisk.ui.AlertsScreen
import com.hackathon.apprisk.ui.CategoriesScreen
import com.hackathon.apprisk.ui.CategoryDetailScreen
import com.hackathon.apprisk.ui.DashboardScreen
import com.hackathon.apprisk.ui.RubricScreen
import com.hackathon.apprisk.ui.SelfTestScreen
import com.hackathon.apprisk.ui.SettingsScreen
import com.hackathon.apprisk.ui.theme.AppRiskTheme

private data class MainTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val mainTabs = listOf(
    MainTab("dashboard", "Home", Icons.Default.Dashboard),
    MainTab("apps", "Apps", Icons.Default.Apps),
    MainTab("categories", "Categories", Icons.Default.Category),
    MainTab("alerts", "Alerts", Icons.Default.Notifications),
    MainTab("settings", "Settings", Icons.Default.Settings),
)

class MainActivity : ComponentActivity() {
    private var openPackage = mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openPackage.value = intent.getStringExtra(EXTRA_OPEN_PACKAGE)
        setContent {
            val vm: AppViewModel = viewModel()
            val darkTheme by vm.darkTheme.collectAsState()
            val snapshot by vm.snapshot.collectAsState()
            AppRiskTheme(darkTheme = darkTheme) {
                val barColor = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
                    @Suppress("DEPRECATION")
                    window.statusBarColor = barColor
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = barColor
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val destination = backStackEntry?.destination
                val currentRoute = destination?.route ?: "dashboard"
                val isDetail = currentRoute.startsWith("app/") ||
                    currentRoute == "rubric" || currentRoute == "self-test"
                val tabIsVisible = mainTabs.any { it.route == currentRoute }

                LaunchedEffect(openPackage.value) {
                    openPackage.value?.let { packageName ->
                        navController.navigate("app/$packageName") { launchSingleTop = true }
                        openPackage.value = null
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    when {
                                        currentRoute.startsWith("app/") -> snapshot.apps.firstOrNull {
                                            it.packageName == backStackEntry?.arguments?.getString("packageName")
                                        }?.appName ?: "App details"
                                        currentRoute == "rubric" -> "Scoring rubric"
                                        currentRoute == "self-test" -> "Self-test"
                                        else -> "App Risk Auditor"
                                    },
                                )
                            },
                            navigationIcon = {
                                if (isDetail) {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            actions = {
                                if (tabIsVisible) {
                                    IconButton(onClick = vm::scan, enabled = !snapshot.isLoading) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                                    }
                                }
                            },
                        )
                    },
                    bottomBar = {
                        if (tabIsVisible) {
                            NavigationBar {
                                mainTabs.forEach { tab ->
                                    val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(tab.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                                        label = { Text(tab.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable("dashboard") {
                            DashboardScreen(vm = vm, navController = navController)
                        }
                        composable("apps") {
                            AppsScreen(vm = vm, navController = navController)
                        }
                        composable("categories") {
                            CategoriesScreen(vm = vm, navController = navController)
                        }
                        composable("alerts") {
                            AlertsScreen(vm = vm, navController = navController)
                        }
                        composable("settings") {
                            SettingsScreen(vm = vm, navController = navController)
                        }
                        composable(
                            "app/{packageName}",
                            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
                        ) { entry ->
                            val packageName = entry.arguments?.getString("packageName").orEmpty()
                            AppDetailScreen(
                                app = snapshot.apps.firstOrNull { it.packageName == packageName },
                                vm = vm,
                            )
                        }
                        composable(
                            "category/{categoryName}",
                            arguments = listOf(navArgument("categoryName") { type = NavType.StringType }),
                        ) { entry ->
                            val category = runCatching {
                                PermissionCategory.valueOf(entry.arguments?.getString("categoryName").orEmpty())
                            }.getOrDefault(PermissionCategory.OTHER)
                            CategoryDetailScreen(category = category, vm = vm, navController = navController)
                        }
                        composable("rubric") { RubricScreen() }
                        composable("self-test") { SelfTestScreen() }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openPackage.value = intent.getStringExtra(EXTRA_OPEN_PACKAGE)
    }

    companion object {
        const val EXTRA_OPEN_PACKAGE = "open_package"
    }
}

package com.gluconote.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.gluconote.ui.home.HomeScreen
import com.gluconote.ui.icons.AppIcons
import com.gluconote.ui.profile.ProfileScreen
import com.gluconote.ui.records.AddRecordScreen
import com.gluconote.ui.records.RecordsScreen
import com.gluconote.ui.stats.StatsScreen
import com.gluconote.ui.theme.Motion

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : Screen("home", "首页", Icons.Filled.Home)
    object Records : Screen("records", "记录", Icons.Filled.List)
    object Stats : Screen("stats", "统计", AppIcons.BarChart)
    object Profile : Screen("profile", "我的", Icons.Filled.Person)
}

/**
 * Two-level nav: outer hosts add/edit (no bottom bar); inner is tab switch.
 * Outer uses shared-axis X + fade; tabs use soft fade-through (no hard cut).
 */
@Composable
fun MainScreen() {
    val rootNav = rememberNavController()
    NavHost(
        navController = rootNav,
        startDestination = "main",
        enterTransition = { Motion.forwardEnter() },
        exitTransition = { Motion.forwardExit() },
        popEnterTransition = { Motion.popEnter() },
        popExitTransition = { Motion.popExit() }
    ) {
        composable("main") {
            MainTabs(rootNav = rootNav)
        }
        composable("add") {
            AddRecordScreen(rootNav, periodArg = "", idArg = "")
        }
        composable(
            route = "add/period/{period}",
            arguments = listOf(navArgument("period") { type = NavType.StringType })
        ) { entry ->
            AddRecordScreen(
                rootNav,
                periodArg = entry.arguments?.getString("period").orEmpty(),
                idArg = ""
            )
        }
        composable(
            route = "add/edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            AddRecordScreen(
                rootNav,
                periodArg = "",
                idArg = entry.arguments?.getString("id").orEmpty()
            )
        }
    }
}

@Composable
private fun MainTabs(rootNav: NavHostController) {
    val tabNav = rememberNavController()
    val items = listOf(Screen.Home, Screen.Records, Screen.Stats, Screen.Profile)
    val navBackStackEntry by tabNav.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected) return@NavigationBarItem
                            tabNav.navigate(screen.route) {
                                popUpTo(tabNav.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            NavHost(
                navController = tabNav,
                startDestination = Screen.Home.route,
                modifier = Modifier.widthIn(max = 720.dp),
                enterTransition = { Motion.tabEnter() },
                exitTransition = { Motion.tabExit() },
                popEnterTransition = { Motion.tabEnter() },
                popExitTransition = { Motion.tabExit() }
            ) {
            composable(Screen.Home.route) { HomeScreen(rootNav) }
            composable(Screen.Records.route) { RecordsScreen(rootNav) }
            composable(Screen.Stats.route) { StatsScreen() }
            composable(Screen.Profile.route) { ProfileScreen(rootNav) }
            }
        }
    }
}

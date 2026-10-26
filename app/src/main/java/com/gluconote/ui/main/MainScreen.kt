package com.gluconote.ui.main

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
import androidx.navigation.compose.NavHost
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gluconote.ui.home.HomeScreen
import com.gluconote.ui.icons.AppIcons
import com.gluconote.ui.profile.ProfileScreen
import com.gluconote.ui.records.RecordsScreen
import com.gluconote.ui.stats.StatsScreen

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : Screen("home", "首页", Icons.Filled.Home)
    object Records : Screen("records?period={period}", "记录", Icons.Filled.List)
    object Stats : Screen("stats", "统计", AppIcons.BarChart)
    object Profile : Screen("profile", "我的", Icons.Filled.Person)
}

@Composable
fun MainScreen() {
    val navController: NavHostController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Records, Screen.Stats, Screen.Profile)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
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
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) { HomeScreen(navController) }
            composable(
                Screen.Records.route,
                arguments = listOf(
                    navArgument("period") {
                        defaultValue = ""
                        nullable = true
                    }
                )
            ) { backStackEntry ->
                val period = backStackEntry.arguments?.getString("period") ?: ""
                RecordsScreen(navController, period)
            }
            composable(Screen.Stats.route) { StatsScreen() }
            composable(Screen.Profile.route) { ProfileScreen(navController) }
        }
    }
}

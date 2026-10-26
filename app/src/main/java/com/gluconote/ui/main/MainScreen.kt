package com.gluconote.ui.main

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
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
import com.gluconote.ui.home.HomeScreen
import com.gluconote.ui.icons.AppIcons
import com.gluconote.ui.profile.ProfileScreen
import com.gluconote.ui.records.AddRecordScreen
import com.gluconote.ui.records.RecordsScreen
import com.gluconote.ui.stats.StatsScreen

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : Screen("home", "\u9996\u9875", Icons.Filled.Home)
    object Records : Screen("records", "\u8bb0\u5f55", Icons.Filled.List)
    object Stats : Screen("stats", "\u7edf\u8ba1", AppIcons.BarChart)
    object Profile : Screen("profile", "\u6211\u7684", Icons.Filled.Person)
}

/**
 * \u4e24\u5c42\u5bfc\u822a\uff1a\u5916\u5c42\u627f\u8f7d\u65b0\u589e/\u7f16\u8f91\uff08\u65e0\u5e95\u680f\uff09\uff1b\u5185\u5c42\u53ea\u505a Tab\u5207\u6362\uff08\u65e0\u52a8\u753b\uff09\u3002
 * \u907f\u514d\u8fdb\u5165\u65b0\u589e\u9875\u65f6 Scaffold \u5e95\u680f\u663e\u9690\u5bfc\u81f4\u6574\u9875\u91cd\u6392\uff0c\u4e5f\u907f\u514d Navigation \u9ed8\u8ba4 ~700ms \u6de1\u5165\u6de1\u51fa\u3002
 */
@Composable
fun MainScreen() {
    val rootNav = rememberNavController()
    NavHost(
        navController = rootNav,
        startDestination = "main",
        enterTransition = { fadeIn(animationSpec = tween(120)) },
        exitTransition = { fadeOut(animationSpec = tween(90)) },
        popEnterTransition = { fadeIn(animationSpec = tween(120)) },
        popExitTransition = { fadeOut(animationSpec = tween(90)) }
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
        NavHost(
            navController = tabNav,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Screen.Home.route) { HomeScreen(rootNav) }
            composable(Screen.Records.route) { RecordsScreen(rootNav) }
            composable(Screen.Stats.route) { StatsScreen() }
            composable(Screen.Profile.route) { ProfileScreen(rootNav) }
        }
    }
}

package com.tunnellight.stop_scroll.ui.nav

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tunnellight.stop_scroll.R
import com.tunnellight.stop_scroll.appContainer
import com.tunnellight.stop_scroll.ui.apps.AppDetailScreen
import com.tunnellight.stop_scroll.ui.apps.AppsScreen
import com.tunnellight.stop_scroll.ui.insights.InsightsScreen
import com.tunnellight.stop_scroll.ui.settings.SettingsScreen
import com.tunnellight.stop_scroll.ui.setup.SetupScreen
import com.tunnellight.stop_scroll.ui.today.TodayScreen
import com.tunnellight.stop_scroll.util.Permissions

private enum class Destination(
    val route: String,
    val label: String,
    val icon: Int,
) {
    TODAY("today", "Today", R.drawable.ic_today),
    INSIGHTS("insights", "Insights", R.drawable.ic_insights),
    APPS("apps", "Apps", R.drawable.ic_apps),
    SETTINGS("settings", "Settings", R.drawable.ic_settings),
}

private const val APP_DETAIL_ROUTE = "app/{packageName}"

@Composable
fun StopScrollRoot() {
    val context = LocalContext.current
    val container = context.appContainer
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    var trackingEnabled by remember {
        mutableStateOf(Permissions.isTrackingServiceEnabled(context))
    }

    LifecycleResumeEffect(Unit) {
        trackingEnabled = Permissions.isTrackingServiceEnabled(context)
        onPauseOrDispose { }
    }

    if (!trackingEnabled && !settings.setupDismissed) {
        SetupScreen(
            onEnable = { Permissions.openAccessibilitySettings(context) },
            onSkip = { container.settings.setSetupDismissed(true) },
        )
    } else {
        MainScaffold()
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    RequestNotificationPermissionOnce()

    Scaffold(
        bottomBar = { BottomBar(navController) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Destination.TODAY.route,
            modifier = Modifier,
        ) {
            composable(Destination.TODAY.route) {
                TodayScreen(
                    contentPadding = padding,
                    onOpenApp = { navController.navigate("app/$it") },
                )
            }
            composable(Destination.INSIGHTS.route) {
                InsightsScreen(
                    contentPadding = padding,
                    onOpenApp = { navController.navigate("app/$it") },
                )
            }
            composable(Destination.APPS.route) {
                AppsScreen(
                    contentPadding = padding,
                    onOpenApp = { navController.navigate("app/$it") },
                )
            }
            composable(Destination.SETTINGS.route) {
                SettingsScreen(contentPadding = padding)
            }
            composable(APP_DETAIL_ROUTE) { entry ->
                val packageName = entry.arguments?.getString("packageName").orEmpty()
                AppDetailScreen(
                    packageName = packageName,
                    onBack = { navController.popBackStack() },
                )
            }
        }
            // The screens are edge-to-edge lists with their titles in the scroll, so without
            // this the content would slide visibly under the clock and battery icons.
            Spacer(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background),
            )
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    if (currentRoute == APP_DETAIL_ROUTE) return

    NavigationBar {
        // Selection follows the primary rather than Material's default secondary container,
        // which keeps the selected tab tied to the app's own accent colour.
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Destination.entries.forEach { destination ->
            NavigationBarItem(
                colors = itemColors,
                selected = currentRoute == destination.route,
                onClick = {
                    if (currentRoute != destination.route) {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(destination.icon),
                        contentDescription = null,
                    )
                },
                label = { Text(destination.label) },
            )
        }
    }
}

/** Asked once, on first entry to the app proper; the nudges are useless without it. */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { },
    )
    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

package com.example.medlog.ui.navigation

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

/** Adaptive navigation wrapper — mirrors Reply's ReplyNavigationWrapper */
@Composable
fun MedLogNavigationWrapper(
    currentDestination: NavDestination?,
    navigateToTopLevel: (TopLevelDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val navLayoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)

    when (navLayoutType) {
        NavigationSuiteType.NavigationDrawer -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet {
                        MedLogNavDrawerContent(
                            currentDestination = currentDestination,
                            navigateToTopLevel = navigateToTopLevel,
                        )
                    }
                },
                content = content,
            )
        }
        NavigationSuiteType.NavigationRail -> {
            NavigationSuiteScaffoldLayout(
                layoutType = navLayoutType,
                navigationSuite = {
                    MedLogNavigationRail(
                        currentDestination = currentDestination,
                        navigateToTopLevel = navigateToTopLevel,
                    )
                },
                content = { content() },
            )
        }
        else -> {
            NavigationSuiteScaffoldLayout(
                layoutType = navLayoutType,
                navigationSuite = {
                    MedLogBottomNavigationBar(
                        currentDestination = currentDestination,
                        navigateToTopLevel = navigateToTopLevel,
                    )
                },
                content = { content() },
            )
        }
    }
}

@Composable
fun MedLogBottomNavigationBar(
    currentDestination: NavDestination?,
    navigateToTopLevel: (TopLevelDestination) -> Unit,
) {
    NavigationBar(modifier = Modifier.fillMaxWidth()) {
        TOP_LEVEL_DESTINATIONS.forEach { dest ->
            NavigationBarItem(
                selected = currentDestination?.hasRoute(dest.route::class) == true,
                onClick = { navigateToTopLevel(dest) },
                icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                label = { Text(stringResource(dest.labelRes)) },
            )
        }
    }
}

@Composable
fun MedLogNavigationRail(
    currentDestination: NavDestination?,
    navigateToTopLevel: (TopLevelDestination) -> Unit,
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        TOP_LEVEL_DESTINATIONS.forEach { dest ->
            NavigationRailItem(
                selected = currentDestination?.hasRoute(dest.route::class) == true,
                onClick = { navigateToTopLevel(dest) },
                icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                label = { Text(stringResource(dest.labelRes)) },
            )
        }
    }
}

@Composable
fun MedLogNavDrawerContent(
    currentDestination: NavDestination?,
    navigateToTopLevel: (TopLevelDestination) -> Unit,
) {
    Text(
        text = "用药日志",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    TOP_LEVEL_DESTINATIONS.forEach { dest ->
        NavigationDrawerItem(
            icon = { Icon(dest.icon, contentDescription = null) },
            label = { Text(stringResource(dest.labelRes)) },
            selected = currentDestination?.hasRoute(dest.route::class) == true,
            onClick = { navigateToTopLevel(dest) },
            colors = NavigationDrawerItemDefaults.colors(),
        )
    }
}

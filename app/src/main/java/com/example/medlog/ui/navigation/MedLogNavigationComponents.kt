package com.example.medlog.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Medication
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

/** 自适应导航容器 — 根据窗口尺寸自动选择底部导航栏 / 侧边轨道 / 永久抽屉 */
@Composable
fun MedLogNavigationWrapper(
    currentDestination: NavDestination?,
    navigateToTopLevel: (TopLevelDestination) -> Unit,
    destinations: List<TopLevelDestination> = TOP_LEVEL_DESTINATIONS,
    content: @Composable () -> Unit,
) {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val navLayoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)

    when (navLayoutType) {
        NavigationSuiteType.NavigationDrawer -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(240.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        MedLogNavDrawerContent(
                            currentDestination = currentDestination,
                            navigateToTopLevel = navigateToTopLevel,
                            destinations = destinations,
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
                        destinations = destinations,
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
                        destinations = destinations,
                    )
                },
                content = {
                    // NavigationBar 已内部消耗 WindowInsets.navigationBars；
                    // 此处再次消耗，确保内层 Scaffold 不会重复添加底部 inset（避免空白间隙）
                    Box(Modifier.consumeWindowInsets(WindowInsets.navigationBars)) {
                        content()
                    }
                },
            )
        }
    }
}

@Composable
fun MedLogBottomNavigationBar(
    currentDestination: NavDestination?,
    navigateToTopLevel: (TopLevelDestination) -> Unit,
    destinations: List<TopLevelDestination> = TOP_LEVEL_DESTINATIONS,
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        destinations.forEach { dest ->
            NavigationBarItem(
                selected = currentDestination?.hasRoute(dest.route::class) == true,
                onClick = { navigateToTopLevel(dest) },
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(stringResource(dest.labelRes)) },
            )
        }
    }
}

@Composable
fun MedLogNavigationRail(
    currentDestination: NavDestination?,
    navigateToTopLevel: (TopLevelDestination) -> Unit,
    destinations: List<TopLevelDestination> = TOP_LEVEL_DESTINATIONS,
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = {
            Icon(
                imageVector = Icons.Rounded.Medication,
                contentDescription = "Anshin",
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        Spacer(Modifier.height(8.dp))
        destinations.forEach { dest ->
            NavigationRailItem(
                selected = currentDestination?.hasRoute(dest.route::class) == true,
                onClick = { navigateToTopLevel(dest) },
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(stringResource(dest.labelRes)) },
            )
        }
    }
}

@Composable
fun MedLogNavDrawerContent(
    currentDestination: NavDestination?,
    navigateToTopLevel: (TopLevelDestination) -> Unit,
    destinations: List<TopLevelDestination> = TOP_LEVEL_DESTINATIONS,
) {
    // 抽屉品牌区——图标 + 应用名称
    Icon(
        imageVector = Icons.Rounded.Medication,
        contentDescription = null,
        modifier = Modifier
            .padding(horizontal = 28.dp, vertical = 20.dp)
            .size(40.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = "用药日志",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 28.dp),
    )
    Spacer(Modifier.height(12.dp))

    destinations.forEach { dest ->
        NavigationDrawerItem(
            icon = { Icon(dest.icon, contentDescription = null) },
            label = { Text(stringResource(dest.labelRes)) },
            selected = currentDestination?.hasRoute(dest.route::class) == true,
            onClick = { navigateToTopLevel(dest) },
            modifier = Modifier.padding(horizontal = 12.dp),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        )
    }
}

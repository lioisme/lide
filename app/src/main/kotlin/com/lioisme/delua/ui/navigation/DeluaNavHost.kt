package com.lioisme.delua.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lioisme.delua.androlua.engine.EngineBridge
import com.lioisme.delua.model.Project
import com.lioisme.delua.project.ProjectManager
import com.lioisme.delua.ui.screen.EditorScreen
import com.lioisme.delua.ui.screen.LibraryManagerScreen
import com.lioisme.delua.ui.screen.ModuleManagerScreen
import com.lioisme.delua.ui.screen.PermissionScreen
import com.lioisme.delua.ui.screen.ProjectDependenciesScreen
import com.lioisme.delua.ui.screen.ProjectListScreen
import com.lioisme.delua.ui.screen.PluginScreen
import com.lioisme.delua.ui.screen.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder

private const val ANIM_DURATION = 300

object Routes {
    const val PROJECT_LIST = "projects"
    const val EDITOR = "editor/{projectName}/{projectPath}"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    const val PLUGINS = "plugins"
    const val LIBRARIES = "libraries"
    const val MODULES = "modules"
    const val DEPENDENCIES = "dependencies/{projectName}/{projectPath}"

    fun editor(project: Project): String {
        return "editor/${enc(project.name)}/${enc(project.path)}"
    }

    fun dependencies(project: Project): String {
        return "dependencies/${enc(project.name)}/${enc(project.path)}"
    }

    fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    fun dec(s: String?) = URLDecoder.decode(s ?: "", "UTF-8")
}

@Composable
fun DeluaNavHost(navController: NavHostController, pendingImportPaths: List<String> = emptyList()) {
    val context = LocalContext.current
    val projectManager = remember { ProjectManager(context) }
    val engineBridge = remember { EngineBridge(context) }

    NavHost(navController = navController, startDestination = Routes.PROJECT_LIST) {
        composable(
            route = Routes.PROJECT_LIST,
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it / 4 }, animationSpec = tween(ANIM_DURATION))
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(ANIM_DURATION))
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(ANIM_DURATION))
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it / 4 }, animationSpec = tween(ANIM_DURATION))
            }
        ) {
            ProjectListScreen(
                projectManager = projectManager,
                onProjectClick = { project ->
                    navController.navigate(Routes.editor(project))
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                },
                pendingImportPaths = pendingImportPaths,
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("projectName") { type = NavType.StringType },
                navArgument("projectPath") { type = NavType.StringType }
            ),
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(ANIM_DURATION))
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(ANIM_DURATION))
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(ANIM_DURATION))
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(ANIM_DURATION))
            }
        ) { backStackEntry ->
            val project = rememberProject(backStackEntry.arguments?.getString("projectName"),
                backStackEntry.arguments?.getString("projectPath"))
            EditorScreen(
                project = project,
                projectManager = projectManager,
                onBack = { navController.popBackStack() },
                onRun = { fileName ->
                    val intent = engineBridge.createEngineIntent(project.path, fileName)
                    context.startActivity(intent)
                },
                onDependencies = {
                    navController.navigate(Routes.dependencies(project))
                }
            )
        }
        composable(
            route = Routes.SETTINGS,
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(ANIM_DURATION))
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(ANIM_DURATION))
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(ANIM_DURATION))
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(ANIM_DURATION))
            }
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPermissionsClick = { navController.navigate(Routes.PERMISSIONS) },
                onPluginsClick = { navController.navigate(Routes.PLUGINS) },
                onLibrariesClick = { navController.navigate(Routes.LIBRARIES) },
                onModulesClick = { navController.navigate(Routes.MODULES) },
            )
        }
        composable(route = Routes.PERMISSIONS) {
            PermissionScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(route = Routes.PLUGINS) {
            PluginScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(route = Routes.LIBRARIES) {
            LibraryManagerScreen(
                projectManager = projectManager,
                onBack = { navController.popBackStack() },
            )
        }
        composable(route = Routes.MODULES) {
            ModuleManagerScreen(
                projectManager = projectManager,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.DEPENDENCIES,
            arguments = listOf(
                navArgument("projectName") { type = NavType.StringType },
                navArgument("projectPath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val project = rememberProject(
                backStackEntry.arguments?.getString("projectName"),
                backStackEntry.arguments?.getString("projectPath")
            )
            ProjectDependenciesScreen(
                project = project,
                projectManager = projectManager,
                onBack = { navController.popBackStack() },
                onNavigateToLibraries = { navController.navigate(Routes.LIBRARIES) },
                onNavigateToModules = { navController.navigate(Routes.MODULES) },
            )
        }
    }
}

@Composable
private fun rememberProject(name: String?, path: String?): Project {
    val n = Routes.dec(name)
    val p = Routes.dec(path)
    return remember(n, p) { Project(name = n, path = p) }
}

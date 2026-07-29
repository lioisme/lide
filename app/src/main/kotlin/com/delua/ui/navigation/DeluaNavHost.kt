package com.delua.ui.navigation

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
import com.delua.androlua.engine.LuaRunner
import com.delua.model.Project
import com.delua.project.ProjectManager
import com.delua.ui.screen.EditorScreen
import com.delua.ui.screen.ProjectListScreen
import com.delua.ui.screen.RunScreen
import com.delua.ui.screen.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder

private const val ANIM_DURATION = 300

object Routes {
    const val PROJECT_LIST = "projects"
    const val EDITOR = "editor/{projectName}/{projectPath}"
    const val RUN = "run/{projectName}/{projectPath}/{fileName}"
    const val SETTINGS = "settings"

    fun editor(project: Project): String {
        return "editor/${enc(project.name)}/${enc(project.path)}"
    }

    fun run(project: Project, fileName: String): String {
        return "run/${enc(project.name)}/${enc(project.path)}/${enc(fileName)}"
    }

    fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    fun dec(s: String?) = URLDecoder.decode(s ?: "", "UTF-8")
}

@Composable
fun DeluaNavHost(navController: NavHostController) {
    val context = LocalContext.current
    val projectManager = remember { ProjectManager(context) }
    val luaRunner = remember { LuaRunner(context) }

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
                    navController.navigate(Routes.run(project, fileName))
                }
            )
        }
        composable(
            route = Routes.RUN,
            arguments = listOf(
                navArgument("projectName") { type = NavType.StringType },
                navArgument("projectPath") { type = NavType.StringType },
                navArgument("fileName") { type = NavType.StringType }
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
            val project = rememberProject(
                backStackEntry.arguments?.getString("projectName"),
                backStackEntry.arguments?.getString("projectPath")
            )
            val fileName = Routes.dec(backStackEntry.arguments?.getString("fileName"))
            RunScreen(
                project = project,
                fileName = fileName,
                luaRunner = luaRunner,
                onBack = { navController.popBackStack() }
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
                onBack = { navController.popBackStack() }
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

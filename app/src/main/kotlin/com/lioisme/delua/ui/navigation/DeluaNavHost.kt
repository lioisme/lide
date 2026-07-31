package com.lioisme.delua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.lioisme.delua.engine.RunEngineLauncher
import com.lioisme.delua.model.Project
import com.lioisme.delua.project.ProjectManager
import com.lioisme.delua.ui.screen.EditorScreen
import com.lioisme.delua.ui.screen.LibraryManagerScreen
import com.lioisme.delua.ui.screen.LuaManualScreen
import com.lioisme.delua.ui.screen.ModuleManagerScreen
import com.lioisme.delua.ui.screen.PermissionScreen
import com.lioisme.delua.ui.screen.ProjectDependenciesScreen
import com.lioisme.delua.ui.screen.ProjectListScreen
import com.lioisme.delua.ui.screen.PluginScreen
import com.lioisme.delua.ui.screen.SettingsScreen
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val PROJECT_LIST = "projects"
    const val EDITOR = "editor/{projectName}/{projectPath}?fileName={fileName}"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    const val PLUGINS = "plugins"
    const val LIBRARIES = "libraries"
    const val MODULES = "modules"
    const val DEPENDENCIES = "dependencies/{projectName}/{projectPath}"
    const val MANUAL = "manual"

    fun editor(project: Project): String {
        return "editor/${enc(project.name)}/${enc(project.path)}?fileName=${enc(project.mainFile)}"
    }

    fun dependencies(project: Project): String {
        return "dependencies/${enc(project.name)}/${enc(project.path)}"
    }

    fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    fun dec(s: String?) = URLDecoder.decode(s ?: "", "UTF-8")
}

@Composable
fun DeluaNavHost(
    navController: NavHostController,
    pendingImportPaths: List<String> = emptyList(),
    pendingOpenFiles: List<Pair<String, String>> = emptyList(),
) {
    val context = LocalContext.current
    val projectManager = remember { ProjectManager(context) }
    val engineLauncher = remember { RunEngineLauncher(context) }

    LaunchedEffect(navController, pendingOpenFiles) {
        val open = pendingOpenFiles.firstOrNull()
        if (open != null) {
            val dir = File(open.first)
            navController.navigate(
                Routes.editor(
                    Project(name = dir.name, path = dir.absolutePath, mainFile = open.second)
                )
            )
        }
    }

    NavHost(navController = navController, startDestination = Routes.PROJECT_LIST) {
        slideComposable(
            route = Routes.PROJECT_LIST,
            transitions = NavTransitions.QUARTER,
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
        slideComposable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("projectName") { type = NavType.StringType },
                navArgument("projectPath") { type = NavType.StringType },
                navArgument("fileName") {
                    type = NavType.StringType
                    defaultValue = "main.lua"
                }
            ),
            // 编辑器与其余页面一致使用经典 slide 转场（产品规范，禁止渐变）。
            // 首帧 jank 已受控，不构成例外理由：
            // - TextMate 一次性初始化在后台线程完成，就绪前仅显示进度圈（LuaEditor 门控）；
            // - 文件内容加载在 IO 线程（EditorSession.initialize）；
            // - 全文诊断经 500ms 防抖 + 后台增量扫描 + 大文件跳过（LuaEditor.kt）。
            // 残余成本集中在首次打开的 CodeEditor 构建（AndroidView factory），
            // 属不可延迟的主线程工作，与转场类型无关；
            // 此前审计 P1-B 以 fade 弱化该帧成本，现按产品规范统一为 slide。
        ) { backStackEntry ->
            val project = rememberProject(
                backStackEntry.arguments?.getString("projectName"),
                backStackEntry.arguments?.getString("projectPath"),
                backStackEntry.arguments?.getString("fileName") ?: "main.lua",
            )
            EditorScreen(
                project = project,
                projectManager = projectManager,
                onBack = { navController.popBackStack() },
                onRun = { fileName ->
                    engineLauncher.launch(project.path, fileName)
                },
                onDependencies = {
                    navController.navigate(Routes.dependencies(project))
                }
            )
        }
        slideComposable(route = Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPermissionsClick = { navController.navigate(Routes.PERMISSIONS) },
                onPluginsClick = { navController.navigate(Routes.PLUGINS) },
                onLibrariesClick = { navController.navigate(Routes.LIBRARIES) },
                onModulesClick = { navController.navigate(Routes.MODULES) },
                onManualClick = { navController.navigate(Routes.MANUAL) },
            )
        }
        slideComposable(route = Routes.MANUAL) {
            LuaManualScreen(
                onBack = { navController.popBackStack() }
            )
        }
        slideComposable(route = Routes.PERMISSIONS) {
            PermissionScreen(
                onBack = { navController.popBackStack() }
            )
        }
        slideComposable(route = Routes.PLUGINS) {
            PluginScreen(
                onRun = { plugin ->
                    engineLauncher.launch(plugin.dir, plugin.main)
                },
                onBack = { navController.popBackStack() }
            )
        }
        slideComposable(route = Routes.LIBRARIES) {
            LibraryManagerScreen(
                projectManager = projectManager,
                onBack = { navController.popBackStack() },
            )
        }
        slideComposable(route = Routes.MODULES) {
            ModuleManagerScreen(
                projectManager = projectManager,
                onBack = { navController.popBackStack() },
            )
        }
        slideComposable(
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
private fun rememberProject(name: String?, path: String?, fileName: String = "main.lua"): Project {
    val n = Routes.dec(name)
    val p = Routes.dec(path)
    val f = Routes.dec(fileName)
    return remember(n, p, f) { Project(name = n, path = p, mainFile = f) }
}

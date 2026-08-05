package com.lioisme.lide.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.lioisme.lide.engine.RunEngineLauncher
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.ProjectManager
import com.lioisme.lide.ui.screen.ApiBrowserScreen
import com.lioisme.lide.ui.screen.ApiClassDetailScreen
import com.lioisme.lide.ui.screen.BuildApkScreen
import com.lioisme.lide.ui.screen.EditorScreen
import com.lioisme.lide.ui.screen.EditorSettingsScreen
import com.lioisme.lide.ui.screen.ExamplesScreen
import com.lioisme.lide.ui.screen.HelpScreen
import com.lioisme.lide.ui.screen.HostPermissionScreen
import com.lioisme.lide.ui.screen.LuaManualScreen
import com.lioisme.lide.ui.screen.PluginScreen
import com.lioisme.lide.ui.screen.ProjectInfoScreen
import com.lioisme.lide.ui.screen.ProjectListScreen
import com.lioisme.lide.ui.screen.ProjectPermissionScreen
import com.lioisme.lide.ui.screen.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val PROJECT_LIST = "projects"
    const val EDITOR = "editor/{projectName}/{projectPath}?fileName={fileName}"
    const val SETTINGS = "settings"
    const val EDITOR_SETTINGS = "editor-settings"
    const val PERMISSIONS = "permissions"
    const val PROJECT_PERMISSIONS = "projectpermissions?projectName={projectName}&projectPath={projectPath}"
    const val PROJECT_INFO = "projectinfo?projectName={projectName}&projectPath={projectPath}"
    const val BUILD_APK = "buildapk?projectName={projectName}&projectPath={projectPath}"
    const val PLUGINS = "plugins"
    const val MANUAL = "manual"
    const val EXAMPLES = "examples"
    const val API_BROWSER = "apibrowser"
    const val API_CLASS_DETAIL = "apibrowser/{className}"
    const val HELP = "help"

    fun editor(project: Project): String = "editor/${enc(project.name)}/${enc(project.path)}?fileName=${enc(project.mainFile)}"

    fun projectPermissions(project: Project): String = "projectpermissions?projectName=${enc(project.name)}&projectPath=${enc(project.path)}"

    fun projectInfo(project: Project): String = "projectinfo?projectName=${enc(project.name)}&projectPath=${enc(project.path)}"

    fun buildApk(project: Project): String = "buildapk?projectName=${enc(project.name)}&projectPath=${enc(project.path)}"

    fun apiClassDetail(className: String): String = "apibrowser/${enc(className)}"

    fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun dec(s: String?) = URLDecoder.decode(s ?: "", "UTF-8")
}

@Composable
fun LideNavHost(
    navController: NavHostController,
    pendingImportPaths: List<String> = emptyList(),
    pendingOpenFiles: List<Pair<String, String>> = emptyList(),
    pendingPluginImport: Boolean = false,
) {
    val context = LocalContext.current
    val projectManager = remember { ProjectManager() }
    val engineLauncher = remember { RunEngineLauncher(context) }
    // WP-004：工程属性保存后自增，驱动列表刷新与编辑器抽屉工程名更新。
    var projectInfoVersion by remember { mutableIntStateOf(0) }

    // 工程列表缓存：状态提升至 NavHost 层（导航往返不销毁）。
    // Compose 导航会销毁非当前目的地的组合，列表状态若留在页面内，
    // 每次从设置/编辑器返回都会重置为空 → 空态闪烁 + 全量磁盘重扫；
    // 提升后返回时先渲染缓存，再由页面触发式后台刷新。
    var projects by remember { mutableStateOf(listOf<Project>()) }
    val refreshProjects: suspend () -> Unit = {
        projects = withContext(Dispatchers.IO) { projectManager.listProjects() }
    }

    LaunchedEffect(navController, pendingOpenFiles) {
        val open = pendingOpenFiles.firstOrNull()
        if (open != null) {
            val dir = File(open.first)
            // 启动期一次性系统导航：等待栈顶就绪（RESUMED）后执行，防止被门控误丢。
            navController.navigateWhenReady(
                Routes.editor(
                    Project(name = dir.name, path = dir.absolutePath, mainFile = open.second),
                ),
            )
        }
    }

    LaunchedEffect(navController, pendingPluginImport) {
        if (pendingPluginImport) {
            navController.navigateWhenReady(Routes.PLUGINS)
        }
    }

    // 导航队列常驻执行：随组合销毁自动取消；保证任何快速输入最终在转场边界上按序执行。
    LaunchedEffect(navController) {
        navController.drainNavigationQueue()
    }

    NavHost(navController = navController, startDestination = Routes.PROJECT_LIST) {
        slideComposable(
            route = Routes.PROJECT_LIST,
            transitions = NavTransitions.QUARTER,
        ) {
            // 根页面兜底：导航忙碌（转场/队列）时返回键入队弹栈，杜绝「排队窗口内误退出」；
            // 空闲时返回 false → 交给系统默认行为（退出 Activity）。
            BackHandler(enabled = true) {
                if (!navController.popGuarded()) {
                    (context as? Activity)?.finish()
                }
            }
            ProjectListScreen(
                projectManager = projectManager,
                projects = projects,
                refresh = refreshProjects,
                onProjectClick = { project ->
                    navController.navigateGuarded(Routes.editor(project))
                },
                onProjectInfoClick = { project ->
                    navController.navigateGuarded(Routes.projectInfo(project))
                },
                onSettingsClick = {
                    navController.navigateGuarded(Routes.SETTINGS)
                },
                refreshKey = projectInfoVersion,
                pendingImportPaths = pendingImportPaths,
            )
        }
        slideComposable(
            route = Routes.EDITOR,
            arguments =
                listOf(
                    navArgument("projectName") { type = NavType.StringType },
                    navArgument("projectPath") { type = NavType.StringType },
                    navArgument("fileName") {
                        type = NavType.StringType
                        defaultValue = "main.lua"
                    },
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
            val project =
                rememberProject(
                    backStackEntry.arguments?.getString("projectName"),
                    backStackEntry.arguments?.getString("projectPath"),
                    backStackEntry.arguments?.getString("fileName") ?: "main.lua",
                )
            // 系统返回键过门控：转场未完成时入队，转场完成后执行（杜绝「转场中弹栈」失步）。
            BackHandler(enabled = true) { navController.popGuarded() }
            EditorScreen(
                project = project,
                projectManager = projectManager,
                onBack = { navController.popGuarded() },
                onPermissions = {
                    navController.navigateGuarded(Routes.projectPermissions(project))
                },
                onProjectInfo = {
                    navController.navigateGuarded(Routes.projectInfo(project))
                },
                onBuildApk = {
                    navController.navigateGuarded(Routes.buildApk(project))
                },
                onRun = { fileName ->
                    engineLauncher.launch(project.path, fileName)
                },
                projectInfoVersion = projectInfoVersion,
            )
        }
        slideComposable(route = Routes.SETTINGS) {
            BackHandler(enabled = true) { navController.popGuarded() }
            SettingsScreen(
                onBack = { navController.popGuarded() },
                onEditorSettingsClick = { navController.navigateGuarded(Routes.EDITOR_SETTINGS) },
                onPermissionsClick = { navController.navigateGuarded(Routes.PERMISSIONS) },
                onPluginsClick = { navController.navigateGuarded(Routes.PLUGINS) },
                onManualClick = { navController.navigateGuarded(Routes.MANUAL) },
                onExamplesClick = { navController.navigateGuarded(Routes.EXAMPLES) },
                onApiBrowserClick = { navController.navigateGuarded(Routes.API_BROWSER) },
                onHelpClick = { navController.navigateGuarded(Routes.HELP) },
            )
        }
        slideComposable(route = Routes.EDITOR_SETTINGS) {
            BackHandler(enabled = true) { navController.popGuarded() }
            EditorSettingsScreen(
                onBack = { navController.popGuarded() },
            )
        }
        slideComposable(route = Routes.HELP) {
            BackHandler(enabled = true) { navController.popGuarded() }
            HelpScreen(
                onBack = { navController.popGuarded() },
            )
        }
        slideComposable(route = Routes.API_BROWSER) {
            BackHandler(enabled = true) { navController.popGuarded() }
            ApiBrowserScreen(
                onBack = { navController.popGuarded() },
                onClassClick = { className ->
                    navController.navigateGuarded(Routes.apiClassDetail(className))
                },
            )
        }
        slideComposable(
            route = Routes.API_CLASS_DETAIL,
            arguments =
                listOf(
                    navArgument("className") { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val className = Routes.dec(backStackEntry.arguments?.getString("className"))
            if (className.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackHandler(enabled = true) { navController.popGuarded() }
            ApiClassDetailScreen(
                className = className,
                onBack = { navController.popGuarded() },
            )
        }
        slideComposable(route = Routes.MANUAL) {
            BackHandler(enabled = true) { navController.popGuarded() }
            LuaManualScreen(
                onBack = { navController.popGuarded() },
            )
        }
        slideComposable(route = Routes.EXAMPLES) {
            BackHandler(enabled = true) { navController.popGuarded() }
            ExamplesScreen(
                onBack = { navController.popGuarded() },
            )
        }
        slideComposable(route = Routes.PERMISSIONS) {
            BackHandler(enabled = true) { navController.popGuarded() }
            HostPermissionScreen(
                onBack = { navController.popGuarded() },
            )
        }
        slideComposable(
            route = Routes.PROJECT_PERMISSIONS,
            arguments =
                listOf(
                    navArgument("projectName") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("projectPath") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
        ) { backStackEntry ->
            val name = Routes.dec(backStackEntry.arguments?.getString("projectName"))
            val path = Routes.dec(backStackEntry.arguments?.getString("projectPath"))
            val project = if (path.isNotBlank()) Project(name = name, path = path, mainFile = "main.lua") else null
            if (project == null) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackHandler(enabled = true) { navController.popGuarded() }
            ProjectPermissionScreen(
                project = project,
                onBack = { navController.popGuarded() },
            )
        }
        slideComposable(route = Routes.PLUGINS) {
            BackHandler(enabled = true) { navController.popGuarded() }
            PluginScreen(
                onRun = { plugin ->
                    // pro 兼容：插件入口收到 {luadir, luapath} 变长参数。
                    // lide 插件独立于 IDE Lua 上下文运行，luadir/luapath 为插件自身
                    // 目录与入口路径（自洽上下文，恒非空）。
                    val mainPath = "${plugin.dir}/${plugin.main}"
                    engineLauncher.launch(
                        plugin.dir,
                        plugin.main,
                        arrayOf(plugin.dir, mainPath),
                    )
                },
                onBack = { navController.popGuarded() },
            )
        }
        slideComposable(
            route = Routes.PROJECT_INFO,
            arguments =
                listOf(
                    navArgument("projectName") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("projectPath") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
        ) { backStackEntry ->
            val name = Routes.dec(backStackEntry.arguments?.getString("projectName"))
            val path = Routes.dec(backStackEntry.arguments?.getString("projectPath"))
            val project = if (path.isNotBlank()) Project(name = name, path = path, mainFile = "main.lua") else null
            if (project == null) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackHandler(enabled = true) { navController.popGuarded() }
            ProjectInfoScreen(
                project = project,
                onBack = { navController.popGuarded() },
                onSaved = {
                    projectInfoVersion++
                    navController.popGuarded()
                },
            )
        }
        slideComposable(
            route = Routes.BUILD_APK,
            arguments =
                listOf(
                    navArgument("projectName") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("projectPath") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
        ) { backStackEntry ->
            val name = Routes.dec(backStackEntry.arguments?.getString("projectName"))
            val path = Routes.dec(backStackEntry.arguments?.getString("projectPath"))
            val project = if (path.isNotBlank()) Project(name = name, path = path, mainFile = "main.lua") else null
            if (project == null) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackHandler(enabled = true) { navController.popGuarded() }
            BuildApkScreen(
                project = project,
                onBack = { navController.popGuarded() },
            )
        }
    }
}

@Composable
private fun rememberProject(
    name: String?,
    path: String?,
    fileName: String = "main.lua",
): Project {
    val n = Routes.dec(name)
    val p = Routes.dec(path)
    val f = Routes.dec(fileName)
    return remember(n, p, f) { Project(name = n, path = p, mainFile = f) }
}

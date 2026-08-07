package com.lioisme.lide.ui.navigation

import android.app.Activity
import android.net.Uri
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
import com.lioisme.lide.ui.screen.AndroLuaSettingsScreen
import com.lioisme.lide.ui.screen.ApiBrowserScreen
import com.lioisme.lide.ui.screen.ApiClassDetailScreen
import com.lioisme.lide.ui.screen.EditorScreen
import com.lioisme.lide.ui.screen.EditorSettingsScreen
import com.lioisme.lide.ui.screen.ExamplesScreen
import com.lioisme.lide.ui.screen.HostPermissionScreen
import com.lioisme.lide.ui.screen.ProjectInfoScreen
import com.lioisme.lide.ui.screen.ProjectListScreen
import com.lioisme.lide.ui.screen.ProjectPermissionScreen
import com.lioisme.lide.ui.screen.SettingsScreen
import com.lioisme.lide.ui.screen.SigningScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Routes {
    const val PROJECT_LIST = "projects"
    const val EDITOR = "editor/{projectName}/{projectPath}?fileName={fileName}"
    const val SETTINGS = "settings"
    const val EDITOR_SETTINGS = "editor-settings"
    const val ANDROLUA_SETTINGS = "androlua-settings"
    const val PERMISSIONS = "permissions"
    const val PROJECT_PERMISSIONS = "projectpermissions?projectName={projectName}&projectPath={projectPath}"
    const val PROJECT_INFO = "projectinfo?projectName={projectName}&projectPath={projectPath}"
    const val SIGNING = "signing"
    const val EXAMPLES = "examples"
    const val API_BROWSER = "apibrowser"
    const val API_CLASS_DETAIL = "apibrowser/{className}"

    fun editor(project: Project): String = "editor/${enc(project.name)}/${enc(project.path)}?fileName=${enc(project.mainFile)}"

    fun projectPermissions(project: Project): String = "projectpermissions?projectName=${enc(project.name)}&projectPath=${enc(project.path)}"

    fun projectInfo(project: Project): String = "projectinfo?projectName=${enc(project.name)}&projectPath=${enc(project.path)}"

    fun apiClassDetail(className: String): String = "apibrowser/${enc(className)}"

    // Uri.encode/decode 与 navigation-compose 的内部解码语义一致（%XX 转义，`+` 保留字面），
    // 取代 URLEncoder/URLDecoder（后者把空格编为 `+`，nav 解码后 `+` 无法还原，含 `+` 的
    // 工程名/路径会被破坏）。
    fun enc(s: String) = Uri.encode(s)

    fun dec(s: String?) = Uri.decode(s ?: "")
}

/**
 * 页面级返回键统一过门控（P4b 去重）：替代每目的地一行
 * `BackHandler(enabled = true) { navController.popGuarded() }`，
 * 转场未完成时入队、完成后执行（杜绝「转场中弹栈」失步）。
 */
@Composable
private fun backGuarded(
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    BackHandler(enabled = true) { navController.popGuarded() }
    content()
}

@Composable
fun LideNavHost(
    navController: NavHostController,
    pendingImportPaths: List<String> = emptyList(),
    pendingOpenFiles: List<Pair<String, String>> = emptyList(),
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
            // 系统返回键统一过门控（见 backGuarded）。
            backGuarded(navController) {
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
                    onManageSigning = {
                        navController.navigateGuarded(Routes.SIGNING)
                    },
                    onRun = { fileName ->
                        engineLauncher.launch(project.path, fileName)
                    },
                    projectInfoVersion = projectInfoVersion,
                )
            }
        }
        slideComposable(route = Routes.SETTINGS) {
            backGuarded(navController) {
                SettingsScreen(
                    onBack = { navController.popGuarded() },
                    onEditorClick = { navController.navigateGuarded(Routes.EDITOR_SETTINGS) },
                    onAndroLuaClick = { navController.navigateGuarded(Routes.ANDROLUA_SETTINGS) },
                    onPermissionsClick = { navController.navigateGuarded(Routes.PERMISSIONS) },
                    onSigningClick = { navController.navigateGuarded(Routes.SIGNING) },
                )
            }
        }
        slideComposable(route = Routes.SIGNING) {
            backGuarded(navController) {
                SigningScreen(
                    onBack = { navController.popGuarded() },
                )
            }
        }
        slideComposable(route = Routes.EDITOR_SETTINGS) {
            backGuarded(navController) {
                EditorSettingsScreen(
                    onBack = { navController.popGuarded() },
                )
            }
        }
        slideComposable(route = Routes.ANDROLUA_SETTINGS) {
            backGuarded(navController) {
                AndroLuaSettingsScreen(
                    onBack = { navController.popGuarded() },
                    onApiBrowserClick = { navController.navigateGuarded(Routes.API_BROWSER) },
                    onExamplesClick = { navController.navigateGuarded(Routes.EXAMPLES) },
                )
            }
        }
        slideComposable(route = Routes.API_BROWSER) {
            backGuarded(navController) {
                ApiBrowserScreen(
                    onBack = { navController.popGuarded() },
                    onClassClick = { className ->
                        navController.navigateGuarded(Routes.apiClassDetail(className))
                    },
                )
            }
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
            backGuarded(navController) {
                ApiClassDetailScreen(
                    className = className,
                    onBack = { navController.popGuarded() },
                )
            }
        }
        slideComposable(route = Routes.EXAMPLES) {
            backGuarded(navController) {
                ExamplesScreen(
                    onBack = { navController.popGuarded() },
                )
            }
        }
        slideComposable(route = Routes.PERMISSIONS) {
            backGuarded(navController) {
                HostPermissionScreen(
                    onBack = { navController.popGuarded() },
                )
            }
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
            backGuarded(navController) {
                ProjectPermissionScreen(
                    project = project,
                    onBack = { navController.popGuarded() },
                )
            }
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
            backGuarded(navController) {
                ProjectInfoScreen(
                    project = project,
                    projectManager = projectManager,
                    onBack = { navController.popGuarded() },
                    onSaved = {
                        projectInfoVersion++
                        navController.popGuarded()
                    },
                )
            }
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

package com.lioisme.lide.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.ProjectManager

/**
 * 编辑器会话宿主（Q8，2026-08-09）：[EditorSession] 的 ViewModel 包装。
 *
 * 生命周期：随导航返回栈条目存活——旋转/配置变更不销毁，打开的标签、选中文件、
 * 撤销历史、脏标记在配置变更后完整保留（此前 `remember(project)` 会话随重组重建，
 * 旋转即丢 tab）；条目真正出栈时 [onCleared] 委托会话兜底保存（脏标签落盘 +
 * 缓存清空 + 作用域取消），与页面销毁语义一致。
 */
class EditorSessionViewModel(
    val session: EditorSession,
) : ViewModel() {

    override fun onCleared() {
        session.release()
    }
}

/**
 * 会话工厂：宿主按导航参数（project）构造 [EditorSession]（dispatcher 走默认
 * 「主线程状态写 + IO 线程文件读写」契约；单测仍直接构造 EditorSession 注入虚拟调度器）。
 */
class EditorSessionViewModelFactory(
    private val project: Project,
    private val projectManager: ProjectManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        EditorSessionViewModel(EditorSession(project, projectManager)) as T
}

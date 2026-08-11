package com.lioisme.lide.ui

import com.lioisme.lide.model.Project

/**
 * 工程列表进程级缓存（U4，2026-08-12）。
 *
 * 目的：Activity 重建（切换语言 per-app locale / 切换外观模式）时
 * [LideNavHost] 的 `remember {}` 状态随之丢失，列表先渲染空态再全量重扫
 * （空态闪烁 + 磁盘 IO）。本缓存跨重建存活，重建后首帧直接渲染缓存数据，
 * 后台静默刷新；导航往返仍由 NavHost 层 remember 缓存承接（无额外开销）。
 *
 * 契约：只承载「最近一次刷新结果」的快照，不持久化（进程被杀即空，与
 * 冷启动重扫语义一致）；写入仅在 [LideNavHost.refreshProjects] 成功后进行，
 * 单线程写（主线程）经 [Volatile] 保证跨线程读取可见性。
 */
object ProjectListCache {
    @Volatile
    var projects: List<Project> = emptyList()
}
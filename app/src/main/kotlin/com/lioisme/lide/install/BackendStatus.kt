package com.lioisme.lide.install

/**
 * 静默安装后端可用性状态（Root / Shizuku / Dhizuku 三通道统一映射）。
 *
 * 供 UI 徽标（状态圆点 + 文案）与授权按钮呈现：
 * - [UNAVAILABLE]：依赖缺失（Shizuku/Dhizuku 未安装；Root 无 su 环境）；
 * - [NOT_RUNNING]：已安装但服务未运行（Shizuku binder 死 / Dhizuku 未激活）；
 * - [PERMISSION_NEEDED]：服务在跑但未授权（可触发授权动作）；
 * - [READY]：已授权，可执行静默安装。
 */
enum class BackendStatus {
    UNAVAILABLE,
    NOT_RUNNING,
    PERMISSION_NEEDED,
    READY,
}

package com.lioisme.lide.install

/**
 * 打包产物静默安装后端（用户偏好，持久化于 [com.lioisme.lide.build.BuildOpts.installBackend]）。
 *
 * [DEFAULT] 为系统安装器（pro 兼容），静默仅当显式选中且对应后端 READY 时启用
 * （决策 D-2，见 docs/in-progress/SILENT_INSTALL_WORK_ORDER.md）。
 */
enum class InstallerBackend {
    /** 系统安装器（ACTION_INSTALL_PACKAGE 确认页）。 */
    DEFAULT,

    /** Shizuku / SUI：`pm install` stdin 管道（Shell 上下文）。 */
    SHIZUKU,

    /** Dhizuku（设备所有者）：PackageInstaller Session 免确认。 */
    DHIZUKU,

    /** Root（libsu）：su shell 内 `pm install -r`。 */
    ROOT,
    ;

    companion object {
        /** 持久化字符串 → 枚举；非法/空值回落 [DEFAULT]（防偏好键残留，同 AfterBuild.parse 约定）。 */
        fun parse(raw: String?): InstallerBackend =
            try {
                if (raw.isNullOrEmpty()) DEFAULT else valueOf(raw)
            } catch (_: IllegalArgumentException) {
                DEFAULT
            }
    }
}

package com.lioisme.lide.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.lioisme.lide.R

/**
 * 权限清单数据源（2026-08-11 全面化；2026-08-12 治理红线修订）：覆盖宿主 manifest
 * 声明的全部 uses-permission（常规 + 特殊），常用权限附别名（搜索命中词）。
 *
 * 供工程属性页权限弹窗与工程权限页（[SearchablePermissionPicker]）共用；
 * [ALL] 为宿主运行时权限页（HostPermissionScreen）保留的兼容视图。
 * 未知权限（工程已声明但目录外）由调用方透传展示，名称原样。
 *
 * 权限与用途分离契约（2026-08-12）：本目录只登记「真权限」；Root / Shizuku /
 * Dhizuku 为宿主呈现的**通用系统级权限通道**（宿主用其实现静默安装等能力），
 * 由权限管理页「通用权限」区统一呈现，**不登记在此目录**（避免宿主权限页
 * 常规区与通用区重复呈现）；工程如需声明 Dhizuku API 等通道权限，经
 * 「其他（未登记）」透传组勾选。
 */
internal enum class PermissionGroup {
    REGULAR,
    SPECIAL,
}

/** 权限条目：全限定名 → 文案资源；[aliases] 为常用别名关键词（中英，参与搜索）。 */
internal data class PermissionEntry(
    val name: String,
    val labelRes: Int,
    val group: PermissionGroup = PermissionGroup.REGULAR,
    val aliases: List<String> = emptyList(),
)

internal object PermissionCatalog {
    val ENTRIES: List<PermissionEntry> =
        listOf(
            // ===== 常规权限（宿主 manifest 声明，按可读性排序） =====
            PermissionEntry(
                Manifest.permission.INTERNET,
                R.string.perm_internet,
                aliases = listOf("联网", "网络", "上网", "network"),
            ),
            PermissionEntry(
                Manifest.permission.ACCESS_NETWORK_STATE,
                R.string.perm_access_network_state,
                aliases = listOf("网络状态", "network"),
            ),
            PermissionEntry(
                Manifest.permission.CHANGE_NETWORK_STATE,
                R.string.perm_change_network_state,
                aliases = listOf("切换网络", "网络", "network"),
            ),
            PermissionEntry(
                Manifest.permission.ACCESS_WIFI_STATE,
                R.string.perm_access_wifi_state,
                aliases = listOf("WiFi", "无线", "wifi"),
            ),
            PermissionEntry(
                Manifest.permission.CHANGE_WIFI_STATE,
                R.string.perm_change_wifi_state,
                aliases = listOf("WiFi", "无线", "wifi"),
            ),
            PermissionEntry(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                R.string.perm_read_external_storage,
                aliases = listOf("存储", "文件", "读存储", "storage"),
            ),
            PermissionEntry(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                R.string.perm_write_external_storage,
                aliases = listOf("存储", "文件", "写存储", "storage"),
            ),
            PermissionEntry(
                Manifest.permission.ACCESS_MEDIA_LOCATION,
                R.string.perm_access_media_location,
                aliases = listOf("媒体", "图片位置", "照片位置", "photo"),
            ),
            PermissionEntry(
                Manifest.permission.CAMERA,
                R.string.perm_camera,
                aliases = listOf("拍照", "摄像头", "camera"),
            ),
            PermissionEntry(
                Manifest.permission.RECORD_AUDIO,
                R.string.perm_record_audio,
                aliases = listOf("录音", "麦克风", "audio"),
            ),
            PermissionEntry(
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                R.string.perm_modify_audio_settings,
                aliases = listOf("音量", "音频", "audio"),
            ),
            PermissionEntry(
                Manifest.permission.ACCESS_FINE_LOCATION,
                R.string.perm_access_fine_location,
                aliases = listOf("定位", "位置", "GPS", "location"),
            ),
            PermissionEntry(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                R.string.perm_access_coarse_location,
                aliases = listOf("定位", "位置", "GPS", "location"),
            ),
            PermissionEntry(
                Manifest.permission.READ_CONTACTS,
                R.string.perm_read_contacts,
                aliases = listOf("联系人", "通讯录", "contacts"),
            ),
            PermissionEntry(
                Manifest.permission.WRITE_CONTACTS,
                R.string.perm_write_contacts,
                aliases = listOf("联系人", "通讯录", "contacts"),
            ),
            PermissionEntry(
                Manifest.permission.READ_CALENDAR,
                R.string.perm_read_calendar,
                aliases = listOf("日历", "calendar"),
            ),
            PermissionEntry(
                Manifest.permission.WRITE_CALENDAR,
                R.string.perm_write_calendar,
                aliases = listOf("日历", "calendar"),
            ),
            PermissionEntry(
                Manifest.permission.READ_PHONE_STATE,
                R.string.perm_read_phone_state,
                aliases = listOf("电话", "设备信息", "手机状态", "phone"),
            ),
            PermissionEntry(
                Manifest.permission.CALL_PHONE,
                R.string.perm_call_phone,
                aliases = listOf("拨号", "打电话", "call"),
            ),
            PermissionEntry(
                Manifest.permission.ANSWER_PHONE_CALLS,
                R.string.perm_answer_phone_calls,
                aliases = listOf("接听", "来电", "answer"),
            ),
            PermissionEntry(
                Manifest.permission.READ_CALL_LOG,
                R.string.perm_read_call_log,
                aliases = listOf("通话记录", "calllog"),
            ),
            PermissionEntry(
                Manifest.permission.WRITE_CALL_LOG,
                R.string.perm_write_call_log,
                aliases = listOf("通话记录", "calllog"),
            ),
            PermissionEntry(
                Manifest.permission.SEND_SMS,
                R.string.perm_send_sms,
                aliases = listOf("短信", "发送短信", "sms"),
            ),
            PermissionEntry(
                Manifest.permission.RECEIVE_SMS,
                R.string.perm_receive_sms,
                aliases = listOf("短信", "接收短信", "sms"),
            ),
            PermissionEntry(
                Manifest.permission.READ_SMS,
                R.string.perm_read_sms,
                aliases = listOf("短信", "读取短信", "sms"),
            ),
            PermissionEntry(
                "android.permission.WRITE_SMS",
                R.string.perm_write_sms,
                aliases = listOf("短信", "写短信", "sms"),
            ),
            PermissionEntry(
                Manifest.permission.RECEIVE_MMS,
                R.string.perm_receive_mms,
                aliases = listOf("彩信", "mms"),
            ),
            PermissionEntry(
                Manifest.permission.BLUETOOTH,
                R.string.perm_bluetooth,
                aliases = listOf("蓝牙", "bluetooth"),
            ),
            PermissionEntry(
                Manifest.permission.BLUETOOTH_ADMIN,
                R.string.perm_bluetooth_admin,
                aliases = listOf("蓝牙", "bluetooth"),
            ),
            PermissionEntry(
                Manifest.permission.NFC,
                R.string.perm_nfc,
                aliases = listOf("近场通信", "nfc"),
            ),
            PermissionEntry(
                Manifest.permission.POST_NOTIFICATIONS,
                R.string.perm_post_notifications,
                aliases = listOf("通知", "推送", "notification"),
            ),
            PermissionEntry(
                Manifest.permission.FOREGROUND_SERVICE,
                R.string.perm_foreground_service,
                aliases = listOf("前台服务", "service"),
            ),
            PermissionEntry(
                Manifest.permission.VIBRATE,
                R.string.perm_vibrate,
                aliases = listOf("振动", "震动", "vibrate"),
            ),
            PermissionEntry(
                Manifest.permission.WAKE_LOCK,
                R.string.perm_wake_lock,
                aliases = listOf("唤醒", "锁屏", "wakelock"),
            ),
            PermissionEntry(
                Manifest.permission.USE_FINGERPRINT,
                R.string.perm_use_fingerprint,
                aliases = listOf("指纹", "生物识别", "fingerprint"),
            ),
            PermissionEntry(
                "android.permission.FLASHLIGHT",
                R.string.perm_flashlight,
                aliases = listOf("闪光灯", "手电筒", "flashlight"),
            ),
            PermissionEntry(
                Manifest.permission.SET_WALLPAPER,
                R.string.perm_set_wallpaper,
                aliases = listOf("壁纸", "wallpaper"),
            ),
            PermissionEntry(
                Manifest.permission.GET_PACKAGE_SIZE,
                R.string.perm_get_package_size,
                aliases = listOf("应用大小", "占用空间", "packagesize"),
            ),
            PermissionEntry(
                Manifest.permission.CLEAR_APP_CACHE,
                R.string.perm_clear_app_cache,
                aliases = listOf("清除缓存", "cache"),
            ),
            PermissionEntry(
                Manifest.permission.KILL_BACKGROUND_PROCESSES,
                R.string.perm_kill_background_processes,
                aliases = listOf("后台进程", "结束进程", "kill"),
            ),
            PermissionEntry(
                "android.permission.DOWNLOAD_WITHOUT_NOTIFICATION",
                R.string.perm_download_without_notification,
                aliases = listOf("下载", "静默下载", "download"),
            ),
            PermissionEntry(
                Manifest.permission.READ_LOGS,
                R.string.perm_read_logs,
                aliases = listOf("日志", "系统日志", "log"),
            ),
            PermissionEntry(
                "android.permission.READ_FRAME_BUFFER",
                R.string.perm_read_frame_buffer,
                aliases = listOf("屏幕", "帧缓冲", "framebuffer"),
            ),
            PermissionEntry(
                Manifest.permission.BATTERY_STATS,
                R.string.perm_battery_stats,
                aliases = listOf("电量", "电池统计", "battery"),
            ),
            PermissionEntry(
                "com.android.launcher.permission.INSTALL_SHORTCUT",
                R.string.perm_install_shortcut,
                aliases = listOf("桌面", "快捷方式", "shortcut"),
            ),
            // ===== 特殊权限（走系统设置页，不经运行时请求） =====
            PermissionEntry(
                Manifest.permission.SYSTEM_ALERT_WINDOW,
                R.string.perm_special_overlay,
                PermissionGroup.SPECIAL,
                aliases = listOf("悬浮窗", "弹窗", "顶层窗口", "overlay"),
            ),
            PermissionEntry(
                Manifest.permission.WRITE_SETTINGS,
                R.string.perm_special_write_settings,
                PermissionGroup.SPECIAL,
                aliases = listOf("系统设置", "修改设置", "settings"),
            ),
            PermissionEntry(
                Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                R.string.perm_special_all_files,
                PermissionGroup.SPECIAL,
                aliases = listOf("所有文件", "存储", "文件管理", "storage"),
            ),
            PermissionEntry(
                Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                R.string.perm_special_battery,
                PermissionGroup.SPECIAL,
                aliases = listOf("电池", "省电", "battery"),
            ),
            PermissionEntry(
                Manifest.permission.REQUEST_INSTALL_PACKAGES,
                R.string.perm_special_install_unknown,
                PermissionGroup.SPECIAL,
                aliases = listOf("安装", "未知来源", "install"),
            ),
            PermissionEntry(
                Manifest.permission.PACKAGE_USAGE_STATS,
                R.string.perm_special_usage_stats,
                PermissionGroup.SPECIAL,
                aliases = listOf("使用情况", "用量统计", "usage"),
            ),
        )

    /** 已登记权限名集合（O(1) 查表）。 */
    val knownNames: Set<String> by lazy { ENTRIES.mapTo(HashSet()) { it.name } }

    /** 宿主运行时权限页兼容视图（name → 文案资源），语义不变。 */
    val ALL: List<Pair<String, Int>> by lazy { ENTRIES.map { it.name to it.labelRes } }

    /**
     * 宿主 manifest 声明的权限集合（含特殊权限），安装周期内不变。
     * 唯一事实源（P4b 去重）：EditorScreen 运行前授权门与 PermissionScreen 列表共用，
     * 查询失败降级为空集（调用方自行判定可授予子集）。
     */
    fun hostDeclaredPermissions(context: Context): Set<String> =
        try {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                ?.toSet() ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }
}

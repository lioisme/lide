package com.lioisme.delua.ui.screen

import android.Manifest

/**
 * 权限名 → 中文标签 清单。供权限页（运行时授权）与工程属性页（user_permission 写回）
 * 共用；未知权限按名称原样展示。
 */
internal object PermissionCatalog {
    val ALL: List<Pair<String, String>> = listOf(
        Manifest.permission.READ_EXTERNAL_STORAGE to "读取外部存储",
        Manifest.permission.WRITE_EXTERNAL_STORAGE to "写入外部存储",
        Manifest.permission.INTERNET to "访问网络",
        Manifest.permission.ACCESS_NETWORK_STATE to "网络状态",
        Manifest.permission.ACCESS_WIFI_STATE to "WiFi状态",
        Manifest.permission.CAMERA to "相机",
        Manifest.permission.RECORD_AUDIO to "录音",
        Manifest.permission.READ_CONTACTS to "读取联系人",
        Manifest.permission.WRITE_CONTACTS to "写联系人",
        Manifest.permission.READ_CALENDAR to "读取日历",
        Manifest.permission.WRITE_CALENDAR to "写日历",
        Manifest.permission.ACCESS_FINE_LOCATION to "精确定位",
        Manifest.permission.ACCESS_COARSE_LOCATION to "粗略定位",
        Manifest.permission.READ_PHONE_STATE to "读取设备信息",
        Manifest.permission.CALL_PHONE to "拨打电话",
        Manifest.permission.SEND_SMS to "发送短信",
        Manifest.permission.READ_SMS to "读取短信",
        Manifest.permission.RECEIVE_SMS to "接收短信",
        Manifest.permission.BLUETOOTH to "蓝牙",
        Manifest.permission.BLUETOOTH_ADMIN to "蓝牙管理",
        Manifest.permission.NFC to "NFC",
        Manifest.permission.POST_NOTIFICATIONS to "通知",
        Manifest.permission.FOREGROUND_SERVICE to "前台服务",
        Manifest.permission.VIBRATE to "振动",
        Manifest.permission.WAKE_LOCK to "唤醒锁",
    )
}

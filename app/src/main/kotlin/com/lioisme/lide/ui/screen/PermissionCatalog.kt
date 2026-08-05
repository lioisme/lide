package com.lioisme.lide.ui.screen

import android.Manifest
import com.lioisme.lide.R

/**
 * 权限名 → 字符串资源 ID 清单。供权限页（运行时授权）与工程属性页（user_permission 写回）
 * 共用；未知权限按名称原样展示。
 */
internal object PermissionCatalog {
    val ALL: List<Pair<String, Int>> =
        listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE to R.string.perm_read_external_storage,
            Manifest.permission.WRITE_EXTERNAL_STORAGE to R.string.perm_write_external_storage,
            Manifest.permission.INTERNET to R.string.perm_internet,
            Manifest.permission.ACCESS_NETWORK_STATE to R.string.perm_access_network_state,
            Manifest.permission.ACCESS_WIFI_STATE to R.string.perm_access_wifi_state,
            Manifest.permission.CAMERA to R.string.perm_camera,
            Manifest.permission.RECORD_AUDIO to R.string.perm_record_audio,
            Manifest.permission.READ_CONTACTS to R.string.perm_read_contacts,
            Manifest.permission.WRITE_CONTACTS to R.string.perm_write_contacts,
            Manifest.permission.READ_CALENDAR to R.string.perm_read_calendar,
            Manifest.permission.WRITE_CALENDAR to R.string.perm_write_calendar,
            Manifest.permission.ACCESS_FINE_LOCATION to R.string.perm_access_fine_location,
            Manifest.permission.ACCESS_COARSE_LOCATION to R.string.perm_access_coarse_location,
            Manifest.permission.READ_PHONE_STATE to R.string.perm_read_phone_state,
            Manifest.permission.CALL_PHONE to R.string.perm_call_phone,
            Manifest.permission.SEND_SMS to R.string.perm_send_sms,
            Manifest.permission.READ_SMS to R.string.perm_read_sms,
            Manifest.permission.RECEIVE_SMS to R.string.perm_receive_sms,
            Manifest.permission.BLUETOOTH to R.string.perm_bluetooth,
            Manifest.permission.BLUETOOTH_ADMIN to R.string.perm_bluetooth_admin,
            Manifest.permission.NFC to R.string.perm_nfc,
            Manifest.permission.POST_NOTIFICATIONS to R.string.perm_post_notifications,
            Manifest.permission.FOREGROUND_SERVICE to R.string.perm_foreground_service,
            Manifest.permission.VIBRATE to R.string.perm_vibrate,
            Manifest.permission.WAKE_LOCK to R.string.perm_wake_lock,
        )
}

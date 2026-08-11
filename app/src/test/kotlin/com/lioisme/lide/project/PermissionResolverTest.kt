package com.lioisme.lide.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionResolverTest {

    @Test
    fun `declared reads array from init_lua`() {
        val content = """
            appname = "demo"
            user_permission = {"android.permission.CAMERA", "android.permission.RECORD_AUDIO"}
        """.trimIndent()
        assertEquals(
            listOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO"),
            PermissionResolver.declared(content),
        )
    }

    @Test
    fun `declared ignores commented block`() {
        val content = """
            --[[
            user_permission = {"android.permission.CAMERA"}
            ]]
            user_permission = {"android.permission.INTERNET"}
        """.trimIndent()
        assertEquals(listOf("android.permission.INTERNET"), PermissionResolver.declared(content))
    }

    @Test
    fun `declared tolerates bare identifiers`() {
        val content = """
            user_permission = {CAMERA, RECORD_AUDIO}
        """.trimIndent()
        assertEquals(listOf("CAMERA", "RECORD_AUDIO"), PermissionResolver.declared(content))
    }

    @Test
    fun `declared returns empty when missing or blank`() {
        assertEquals(emptyList<String>(), PermissionResolver.declared(null))
        assertEquals(emptyList<String>(), PermissionResolver.declared(""))
        assertEquals(emptyList<String>(), PermissionResolver.declared("appname = \"x\""))
    }

    @Test
    fun `runtime requestable filters special permissions`() {
        assertTrue(PermissionResolver.isRuntimeRequestable("android.permission.CAMERA"))
        assertTrue(PermissionResolver.isRuntimeRequestable("android.permission.INTERNET"))
        assertFalse(PermissionResolver.isRuntimeRequestable("android.permission.MANAGE_EXTERNAL_STORAGE"))
        assertFalse(PermissionResolver.isRuntimeRequestable("android.permission.SYSTEM_ALERT_WINDOW"))
        assertFalse(PermissionResolver.isRuntimeRequestable("android.permission.WRITE_SETTINGS"))
    }

    @Test
    fun `runtimeRequestable keeps order and drops special`() {
        val declared = listOf(
            "android.permission.CAMERA",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.RECORD_AUDIO",
        )
        assertEquals(
            listOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO"),
            PermissionResolver.runtimeRequestable(declared),
        )
    }

    @Test
    fun `runtimeRequestable keeps unknown namespaces`() {
        assertEquals(
            listOf("com.example.CUSTOM"),
            PermissionResolver.runtimeRequestable(listOf("com.example.CUSTOM")),
        )
    }

    @Test
    fun `normalize prefixes bare names`() {
        assertEquals("android.permission.CAMERA", PermissionResolver.normalize("CAMERA"))
        assertEquals("android.permission.RECORD_AUDIO", PermissionResolver.normalize(" RECORD_AUDIO "))
    }

    @Test
    fun `normalize keeps fully qualified names untouched`() {
        assertEquals(
            "com.android.launcher.permission.INSTALL_SHORTCUT",
            PermissionResolver.normalize("com.android.launcher.permission.INSTALL_SHORTCUT"),
        )
        assertEquals(
            "android.permission.CAMERA",
            PermissionResolver.normalize("android.permission.CAMERA"),
        )
        assertEquals("org.chromium.FOO", PermissionResolver.normalize("org.chromium.FOO"))
        assertEquals("", PermissionResolver.normalize(""))
    }
}

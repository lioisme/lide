package com.lioisme.lide.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [InstallerBackend.parse] 回落契约（SILENT_INSTALL，2026-08-09）：
 * 持久化偏好键残留（非法/空/改名）一律回落 [InstallerBackend.DEFAULT]，同 AfterBuild.parse 约定。
 */
class InstallerBackendTest {
    @Test
    fun `parse null or empty falls back to DEFAULT`() {
        assertEquals(InstallerBackend.DEFAULT, InstallerBackend.parse(null))
        assertEquals(InstallerBackend.DEFAULT, InstallerBackend.parse(""))
        assertEquals(InstallerBackend.DEFAULT, InstallerBackend.parse("   "))
    }

    @Test
    fun `parse unknown value falls back to DEFAULT`() {
        assertEquals(InstallerBackend.DEFAULT, InstallerBackend.parse("MAGISK"))
        assertEquals(InstallerBackend.DEFAULT, InstallerBackend.parse("shizuku"))
        assertEquals(InstallerBackend.DEFAULT, InstallerBackend.parse("Root ")) // 大小写/空白不宽容
    }

    @Test
    fun `parse known values round-trips`() {
        assertEquals(InstallerBackend.DEFAULT, InstallerBackend.parse(InstallerBackend.DEFAULT.name))
        assertEquals(InstallerBackend.SHIZUKU, InstallerBackend.parse(InstallerBackend.SHIZUKU.name))
        assertEquals(InstallerBackend.DHIZUKU, InstallerBackend.parse(InstallerBackend.DHIZUKU.name))
        assertEquals(InstallerBackend.ROOT, InstallerBackend.parse(InstallerBackend.ROOT.name))
    }
}

/**
 * [InstallerCoordinator.resolveBackend] 纯函数契约（SILENT_INSTALL，决策 D-2）：
 * - 偏好 DEFAULT → 恒回落系统安装器（pro 兼容，不自动静默）；
 * - 偏好非 DEFAULT 且实时 READY → 该后端（静默）；
 * - 偏好非 DEFAULT 但实时不可用 → 回落系统安装器。
 */
class InstallerCoordinatorTest {
    private fun resolve(preference: InstallerBackend, ready: Set<InstallerBackend>): InstallerBackend =
        InstallerCoordinator.resolveBackend(preference) { it in ready }

    @Test
    fun `DEFAULT preference never resolves to silent backend`() {
        assertEquals(InstallerBackend.DEFAULT, resolve(InstallerBackend.DEFAULT, setOf(InstallerBackend.SHIZUKU)))
        assertEquals(InstallerBackend.DEFAULT, resolve(InstallerBackend.DEFAULT, setOf(InstallerBackend.DHIZUKU)))
        assertEquals(InstallerBackend.DEFAULT, resolve(InstallerBackend.DEFAULT, setOf(InstallerBackend.ROOT)))
        assertEquals(InstallerBackend.DEFAULT, resolve(InstallerBackend.DEFAULT, emptySet()))
    }

    @Test
    fun `ready backend resolves to itself`() {
        assertEquals(InstallerBackend.SHIZUKU, resolve(InstallerBackend.SHIZUKU, setOf(InstallerBackend.SHIZUKU)))
        assertEquals(InstallerBackend.DHIZUKU, resolve(InstallerBackend.DHIZUKU, setOf(InstallerBackend.DHIZUKU)))
        assertEquals(InstallerBackend.ROOT, resolve(InstallerBackend.ROOT, setOf(InstallerBackend.ROOT)))
    }

    @Test
    fun `not-ready backend falls back to DEFAULT`() {
        assertEquals(InstallerBackend.DEFAULT, resolve(InstallerBackend.SHIZUKU, emptySet()))
        assertEquals(InstallerBackend.DEFAULT, resolve(InstallerBackend.DHIZUKU, setOf(InstallerBackend.SHIZUKU)))
        assertEquals(InstallerBackend.DEFAULT, resolve(InstallerBackend.ROOT, setOf(InstallerBackend.DHIZUKU)))
    }

    @Test
    fun `isReady callback never consulted for DEFAULT preference`() {
        var consulted = false
        val backend =
            InstallerCoordinator.resolveBackend(InstallerBackend.DEFAULT) {
                consulted = true
                true
            }
        assertEquals(InstallerBackend.DEFAULT, backend)
        assertFalse("isReady must not be invoked for DEFAULT preference", consulted)
    }

    @Test
    fun `isReady exception degrades to DEFAULT`() {
        val backend =
            InstallerCoordinator.resolveBackend(InstallerBackend.SHIZUKU) {
                throw IllegalStateException("backend check failed")
            }
        assertEquals(InstallerBackend.DEFAULT, backend)
    }

    @Test
    fun `BackendStatus states are distinct`() {
        val states = BackendStatus.entries.toSet()
        assertEquals(4, states.size)
        assertTrue(BackendStatus.READY in states)
        assertTrue(BackendStatus.PERMISSION_NEEDED in states)
        assertTrue(BackendStatus.NOT_RUNNING in states)
        assertTrue(BackendStatus.UNAVAILABLE in states)
    }
}

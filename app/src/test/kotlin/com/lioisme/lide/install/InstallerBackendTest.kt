package com.lioisme.lide.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [InstallerBackend.parse] 回落契约（SILENT_INSTALL，2026-08-09）：
 * 持久化偏好键残留（非法/空/改名）一律回落 [InstallerBackend.DEFAULT]，同 AfterBuild.parse 约定。
 *
 * 2026-08-12（DEAD_CODE_CLEANUP T5）：[InstallerCoordinator.resolveBackend] 纯函数已删除
 * （零生产调用——生产路径由 BuildApkDialog / InstallMethodDialog 内联判定），原
 * InstallerCoordinatorTest 用例随之移除；保留 [BackendStatus] 枚举契约断言。
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
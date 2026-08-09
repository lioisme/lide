package com.lioisme.lide.build

import org.junit.Test
import java.io.File

/** 临时诊断（不入库）：dump 合成 arsc 供 Python 独立解析对照。 */
class DiagDumpTest {
    @Test
    fun dumpSyntheticArsc() {
        val dir = File("build/diag-arsc").apply { mkdirs() }
        File(dir, "synth-utf8.bin").writeBytes(
            TestArscFactory.syntheticArsc(),
        )
        File(dir, "synth-utf16.bin").writeBytes(
            TestArscFactory.syntheticArsc(utf8 = false),
        )
        File(dir, "synth-obf.bin").writeBytes(
            TestArscFactory.syntheticArsc(iconKey = "icon", iconPath = "res/9T.png"),
        )
    }
}
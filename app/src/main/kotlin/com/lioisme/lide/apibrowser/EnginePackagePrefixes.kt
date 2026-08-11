package com.lioisme.lide.apibrowser

/**
 * 引擎包前缀单一事实源（2026-08-11 解耦审计 D-1 收口）。
 *
 * API 浏览器全部引擎归属判定消费本表，禁止分类器自建引擎前缀列表——
 * 新增引擎包（或引擎包改名）只改本文件，分类器逻辑零改动（EngineSurfaceConsistencyTest
 * 继续守护精确类名单，两者互补：本表管前缀分流，测试管类级一致性）。
 */
object EnginePackagePrefixes {
    /** AndroLua 引擎全量包（分类器 ANDROLUA 归属判定）。 */
    val ANDROLUA_PACKAGES =
        listOf(
            "com.androlua.",
            "com.luajava.",
            "com.nirenr.",
            "com.android.cglib.",
        )

    /** JNI 桥接包（用途分类器 UTILITY 归属判定，为 [ANDROLUA_PACKAGES] 子集）。 */
    val JNI_BRIDGE_PACKAGES =
        listOf(
            "com.luajava.",
            "com.android.cglib.",
        )
}

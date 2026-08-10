// 依赖仓库统一收归（审计 P2#16 同族）：原 5 处模块级 repositories 重复声明合并于此，
// FAIL_ON_PROJECT_REPOS 强制后续模块禁止自行声明仓库（杜绝地址漂移与漏改）。
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
        // libsu（com.github.topjohnwu.libsu）仅在 JitPack 发布（SILENT_INSTALL root 通道，
        // 2026-08-09；版本对齐 komi-store gradle/libs.versions.toml 6.0.0）。
        maven { url = uri("https://jitpack.io") }
    }
}

pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "lide"
include(":app")
include(":androlua")
// androlua 属上游库（AndroLua Pro v4.4.2，允许非逻辑适配），源码收纳在 app/vendor/ 统一管理。
// 用户红线（2026-08-09 源码组织三需求）：所有源代码一律位于 app/ 目录。
project(":androlua").projectDir = file("app/vendor/androlua")
// sora-editor（LGPL-2.1）0.24.6 源码收纳（editor + language-textmate），见 app/vendor/sora-editor/README.md
include(":sora-editor")
include(":sora-editor:editor")
include(":sora-editor:language-textmate")
project(":sora-editor").projectDir = file("app/vendor/sora-editor")
project(":sora-editor:editor").projectDir = file("app/vendor/sora-editor/editor")
project(":sora-editor:language-textmate").projectDir = file("app/vendor/sora-editor/language-textmate")

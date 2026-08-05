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
include(":editor")
include(":androlua")
// androlua 属上游库，源码收纳在 third_party/ 统一管理（后续新增上游库均入此处）
project(":androlua").projectDir = file("third_party/androlua")
// sora-editor（LGPL-2.1）0.24.6 源码收纳（editor + language-textmate），见 third_party/sora-editor/README.md
include(":sora-editor")
include(":sora-editor:editor")
include(":sora-editor:language-textmate")
project(":sora-editor").projectDir = file("third_party/sora-editor")
project(":sora-editor:editor").projectDir = file("third_party/sora-editor/editor")
project(":sora-editor:language-textmate").projectDir = file("third_party/sora-editor/language-textmate")

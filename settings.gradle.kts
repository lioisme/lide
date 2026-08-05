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

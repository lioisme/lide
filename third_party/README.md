# third_party — 上游库统一收纳

本目录集中管理所有**引入本项目的上游库**（源码模块 / 二进制产物 / 补丁），只读；后续新增上游库一律入库在此，禁止平铺在项目根目录。

| 目录 | 内容 | 说明 |
|---|---|---|
| `androlua/` | AndroLua Java 运行时源码（Gradle 库模块） | 模块名保持 `:androlua`，`settings.gradle.kts` 用 `projectDir` 指向本目录 |
| `prebuilt/` | Lua 原生库（`libluajava.so` 等，arm64-v8a/armeabi-v7a/x86_64）与 Lua 模块脚本 | 上游引擎产物归档，`scripts/rebuild_engine.*` 重建后同步 `app/src/main/jniLibs/` |
| `patches/` | 第三方代码补丁（如 `material3_searchbar_patch`） | 实验/历史归档，未接入构建 |

> 上游参考源码 / 参考工程（sora-editor-main、luaj、Squircle-CE、AndroLua_pro 等）仍收纳在
> `docs/reference/`（文档数据湖，git ignored、只读、不提交；部分被构建链引用）。
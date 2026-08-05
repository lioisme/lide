# third_party — 上游库统一收纳

本目录集中管理所有**引入本项目的上游库**（源码模块 / 二进制产物 / 补丁），只读；后续新增上游库一律入库在此，禁止平铺在项目根目录。

| 目录 | 内容 | 说明 |
|---|---|---|
| `androlua/` | AndroLua Java 运行时源码（Gradle 库模块） | 模块名保持 `:androlua`，`settings.gradle.kts` 用 `projectDir` 指向本目录 |
| `sora-editor/` | sora-editor 0.24.6 源码（`:sora-editor:editor` + `:sora-editor:language-textmate`，LGPL-2.1） | 自写最小构建，不引上游 build-logic；oniguruma 原生模块未收纳，用 Joni 纯 Java 路径（见该目录 README） |
| `patches/` | 第三方代码补丁（如 `material3_searchbar_patch`） | 实验/历史归档，未接入构建 |

> 上游参考源码 / 参考工程（sora-editor-main、luaj、Squircle-CE、AndroLua_pro 等）仍收纳在
> `reference/`（参考基线数据湖，git ignored、只读、不提交；2026-08 自 docs/reference/ 迁出）。
>
> 原生引擎二进制已去重（2026-08 T2），源码已归一（2026-08 T11）：不再保留 `third_party/prebuilt/`，
> 也不再有 `app/src/main/jniLibs/` 缓存目录；引擎 `.so` 唯一真源为 `app/src/main/cpp/`，
> 由 AGP `externalNativeBuild`（ndk-build）在 `assemble` 时直接从源码构建并打进 APK。
> Lua 模块脚本唯一真源为 `app/src/main/assets/lua/`。

### 引擎 C 源码（vendored，2026-08 T1 入库 / T11 归一）

AndroLua 引擎 JNI C/C++ 源码（上游 `AndroLua_pro` 派生，GPL 许可证，见
`third_party/androlua/` 内 LICENSE*.txt）已完整 vendor 并统一归档到 `app/src/main/cpp/`：

- 位置：`app/src/main/cpp/<模块>/`（与 lide 定制版 `Android.mk` 同目录，AGP 构建直接引用该源码）；
  `app/src/main/cpp/live2d/` 为 Live2D 独立模块（同一 ndk-build 工程，链接近邻 `luajava`）。
- 来源：AndroLuaPro v4.4.2 上游 `app/src/main/jni`（原版基线已去重归档，恢复配方见 `reference/README.md`；构建链已不再引用）。
- 排除：disabled 模块 `lluv`/`tcc`（luv 已代替 lluv；tcc 无 arm64/x86_64 目标）、独立可执行文件
  `lua.c`/`luac.c`、上游源码中的 Android.mk/Application.mk/Makefile 等构建配置。
- 构建入口：AGP `externalNativeBuild`（`app/build.gradle.kts`，ndk-build 指向 `app/src/main/cpp/Android.mk`）；
  `scripts/rebuild_engine.*` 已废弃删除。
- 补丁：lsqlite3/mkstemp 的 lide 修正已并入 `app/src/main/cpp/` 对应源文件（`build-scripts/patches/` 已删除）。
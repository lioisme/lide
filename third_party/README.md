# third_party — 上游库统一收纳

本目录集中管理所有**引入本项目的上游库**（源码模块 / 二进制产物 / 补丁），只读；后续新增上游库一律入库在此，禁止平铺在项目根目录。

| 目录 | 内容 | 说明 |
|---|---|---|
| `androlua/` | AndroLua Java 运行时源码（Gradle 库模块） | 模块名保持 `:androlua`，`settings.gradle.kts` 用 `projectDir` 指向本目录 |
| `patches/` | 第三方代码补丁（如 `material3_searchbar_patch`） | 实验/历史归档，未接入构建 |

> 上游参考源码 / 参考工程（sora-editor-main、luaj、Squircle-CE、AndroLua_pro 等）仍收纳在
> `docs/reference/`（文档数据湖，git ignored、只读、不提交）。
>
> 原生引擎二进制已去重（2026-08 T2）：不再保留 `third_party/prebuilt/`，`.so` 唯一真源为
> `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/`，Lua 模块脚本唯一真源为
> `app/src/main/assets/lua/`，均由 `scripts/rebuild_engine.*` 重建产出。

### 引擎 C 源码（vendored，2026-08 T1 入库）

AndroLua 引擎 JNI C/C++ 源码（上游 `AndroLua_pro` 派生，GPL 许可证，见
`docs/reference/androlua/` 内 LICENSE）已完整 vendor 到 `build-scripts/jni/`：

- 位置：`build-scripts/jni/<模块>/`（与 lide 定制版 `Android.mk` 同目录，构建直接引用该源码）。
- 来源：`docs/reference/androlua/ AndroLua_pro-master/app/src/main/jni`（历史参考，构建链已不再引用）。
- 排除：disabled 模块 `lluv`/`tcc`（luv 已代替 lluv；tcc 无 arm64/x86_64 目标）、独立可执行文件
  `lua.c`/`luac.c`、上游源码中的 Android.mk/Application.mk/Makefile 等构建配置。
- 重建入口：`scripts/rebuild_engine.ps1|.sh`（`JNI_SRC_DIR` 指向仓内 `build-scripts/jni`）。
- 补丁：`build-scripts/patches/` 覆盖 vendor 拷贝（lsqlite3、zip），vendor 副本保持原样。
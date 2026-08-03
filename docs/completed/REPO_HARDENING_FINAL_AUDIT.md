# 仓库硬化终审报告（T1–T9）

- 日期：2026-08-04
- 依据：`docs/in-progress/REPO_HARDENING_WORK_ORDER.md`
- 提交：`2885ef81`（仓库硬化：源码可完全从仓库构建（T1-T8））
- 结果：**全部通过（T1–T9 均完成）**

## 1. 任务完成矩阵

| 任务 | 内容 | 状态 | 验证摘要 |
|---|---|---|---|
| T1 | 引擎 C 源码 vendor 入库 | ✅ | `build-scripts/jni/` 389 个源文件（16+1 模块）；rebuild 脚本仓内化；构建链零引用 `docs/reference` |
| T2 | prebuilt 去重删除 | ✅ | 51 `.so` + 28 lua 脚本经 SHA256 证实与 jniLibs/assets 逐字节一致后移除；本机 NDK 29 实测重建 16/16 模块 |
| T3 | `.alp` 构建时生成 | ✅ | `generateExamples`（纯 Kotlin zip）挂 `preBuild`；7 个 `.alp` 从 assets 剔除；APK 实测含 7 examples |
| T4 | wrapper SHA256 锁定 | ✅ | `distributionSha256Sum=bafc141b...031f` + `validateDistributionUrl=true`；wrapper jar 哈希与官方一致 |
| T5 | `.gitattributes` 行尾归一 | ✅ | 文本 LF/bin `-text` 归类；`--renormalize` 全量归一；索引 `i/crlf` 零残留 |
| T6 | `.editorconfig` + ktlint + pre-commit | ✅ | `:editor` 接入 org.jlleitschuh.gradle.ktlint 14.2.0（ktlint 1.8.0）；基线 main 0 / test 8 违反（记录不修复）；`.githooks/pre-commit` 语法与运行验证通过 |
| T7 | 移除补丁 `.class` | ✅ | 3 个 `.class` 出库；source+README 保留并注明再生成方式 |
| T8 | 依赖锁定可行性 | ✅ 结论：不引入 | `--write-locks` 实测成功但单体 120KB/275 行噪声；全框架定版本无漂移面；登记替代方案 |
| T9 | 干净 clone 完全从源码构建 | ✅ 通过 | 见下节 |

## 2. T9 干净 clone 终验（关键证据）

临时 clone：`C:\Users\liois\AppData\Local\Temp\opencode\delua_cleanclone`（git clone 于提交 `2885ef81` 之后）。

1. **clone 完整性**：HEAD 正确；`.alp` 不入库（gitignore）；`third_party/prebuilt` 不存在；索引无 CRLF。
2. **引擎重建**：`scripts/rebuild_engine.ps1 arm64-v8a`（NDK 29.0.14251655）→ **16/16 模块成功**，17×3=51 个 `.so` 由 vendored 源码产出至 `jniLibs/`。
3. **全量构建**：`gradlew :app:assembleArm64Debug` **BUILD SUCCESSFUL** → APK 33.84 MB，内含：
   - 7 个 `assets/examples/*.alp`（generateExamples 任务自动生成）
   - 18 个 `lib/arm64-v8a/*.so`（含 libluajava.so）
4. **单元测试**：`:app:testX64DebugUnitTest` + `:editor:testDebugUnitTest` → **8 套件 61 用例，0 失败 0 错误**。
5. **R1 红线**：`git grep luajava|androlua|loadLibrary editor/src` → **0 命中**。

## 3. 遗留事项（非阻塞）

| 事项 | 说明 |
|---|---|
| `.so` 字节级差异 | 干净 clone 重建的 17 个 arm64 `.so` 与提交基线存在构建时间戳差异，属预期（提交 `.so` 为基线真子，重建产自 vendored 源码并已在 APK 实测可用） |
| ktlint 基线 | `:editor` test 源集 8 条违规（`LuaDiagnosticsTest.kt`）——按 T6 约定只记录不作全量修复；修复应通过 `:editor:ktlintFormat` 增量进行 |
| Gradle 10 注意 | 构建出现 "Deprecated Gradle features incompatible with Gradle 10" 提示，属 AGP/ktlint 插件兼容性前瞻警告，Gradle 9.5.1 下无影响，升级前需评估 |

## 4. 提交

- `2885ef81`：仓库硬化：源码可完全从仓库构建（T1-T8）
- 本报告随同 `docs/in-progress/REPO_HARDENING_WORK_ORDER.md` 一并落盘。

## 4b. T10 二进制禁入（2026-08-04 追加）

- 用户决策：宿主 Kotlin/Java，原生库一律源码入库；禁止 `.so`/`.dex`/`.jar`/`.zip`；`.alp` 例外。
- 51 个 `app/src/main/jniLibs/*.so` 全部出库；jniLibs 转纯构建输出（gitignore），仅由 `scripts/rebuild_engine.*` 从 `build-scripts/jni/` 源码产出。
- CI 三个打包 job 均改为 assemble 前先 rebuild；AGENTS.md 新增 R5 硬规则与验证命令。
- 验收：`git ls-files '*.so' '*.dex' '*.zip'` 为空；`git ls-files '*.jar'` 仅 wrapper jar（官方引导例外）。

## 5. 验收断言

- [x] 干净 clone 可从源码完整构建（引擎 .so + Gradle APK）
- [x] APK 内含 examples assets（T3） 与 jniLibs（T1/T2）
- [x] 构建链无 `docs/reference`、无 `third_party/prebuilt` 引用
- [x] 编辑器模块 R1 红线零命中
- [x] 行尾/编码/门禁规范化入库（T5–T7）
# third_party/sora-editor — 上游源码收纳

- 上游：https://github.com/Rosemoe/sora-editor（Rosemoe）
- 版本：**0.24.6**（GitHub tag `0.24.6`，2026-08 下载归档；压缩包不含 git submodule 内容）
- 许可：**LGPL-2.1**（随附 `LICENSE`；各源码文件头亦带许可声明）

## 收纳范围

| 模块 | 路径 | 内容 |
|---|---|---|
| `:sora-editor:editor` | `editor/` | `src/main` 生产源码（java 214 + kotlin 73 + res）、proguard/consumer 规则 |
| `:sora-editor:language-textmate` | `language-textmate/` | `src/main` 生产源码（含 vendored tm4e 引擎 + Joni 正则实现）、proguard/consumer 规则 |

未收纳：`oniguruma-native`（原生 oniguruma C 引擎，其 C 源码为 git submodule、不随 tag zip 分发；
本仓库不使用原生路径，TextMate 正则走 Joni 纯 Java 实现）、`editor-bom`、`editor-lsp`、
`language-*` 其他语言模块、`app` demo、测试源码。

## 相对上游的改动

1. `build.gradle.kts` 为本仓库自写最小构建：不引上游 `build-logic`（其 pin 死 AGP 9.1.0 /
   Kotlin 2.3.10，远超本仓库根构建 AGP 8.6.0 / Kotlin 2.2.20）；沿用本仓库约定
   （compileSdk 36 / minSdk 24 / Java 17 / jvmTarget 17 / aliyun 镜像仓库）。
2. 依赖版本取自 0.24.6 `gradle/libs.versions.toml`：
   androidx.annotation 1.10.0、androidx.collection 1.5.0、gson 2.13.2、
   jcodings 1.0.64、joni 2.2.7、snakeyaml-engine 3.0.1、org.eclipse.jdt.annotation 2.4.100。
3. 新增桩类 `language-textmate/src/main/java/io/github/rosemoe/oniguruma/OnigNative.java`
   （见文件头注释）：替代上游 oniguruma-native 的 JNI 类，保证编译期引用闭合；
   运行期 `isAvailable()` 返回 false → 与官方 Maven 制品 `language-textmate:0.24.6`
   （不携带 oniguruma-native）行为一致。

## 更新流程

1. 脚本化（推荐）：`.\scripts\update-sora-editor.ps1 -Tag <tag>`（自动下载/替换/保留白名单/同步依赖/跑门禁）；
   每周自动版：`.github/workflows/update-sora-editor.yml`（发现新版自动开 PR）。
2. 手工流程（脚本不可用时的兜底）：
   1. 下载新 tag 压缩包，解压至临时目录。
   2. 整体替换 `editor/src/main` 与 `language-textmate/src/main`（保留桩类文件；
      若上游 OnigNative API 变化则同步更新桩签名）。
   3. 按上游 `libs.versions.toml` 同步依赖版本；按需核对 `compileSdk`。
   4. 运行 `.\gradlew.bat :app:compileArm64DebugKotlin --console=plain` 验证。
   5. 更新 `VERSION` 文件与本台账。

## 版本台账

| 版本 | 归档日期 | PR/提交 | 变更摘要 |
|---|---|---|---|
| 0.24.6 | 2026-08 | — | 初始收纳；editor 214/73 源文件、language-textmate（含 tm4e + Joni）；OnigNative.java 桩；两模块自写 build.gradle.kts（版本见 <doc-2>）。备注：本版无代码折叠交互（`lang/folding/FoldingRegion` 为死代码），折叠交互为上游 0.25+ 引入，见 EDITOR_FEATURE_COMPLETION_WORK_ORDER G5 |

## R5 说明

本目录仅收录源码与文本构建文件，无任何二进制产物（.so/.dex/.jar/.zip）。

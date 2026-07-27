# Delua 项目安全迁移工作清单与注意事项

## 基准版本：AndroLuaX → Delua 零闭源重构

---

## 文件状态

| 项目 | 内容 |
|---|---|
| **文档版本** | 1.0 |
| **分类** | 安全架构与实施 |
| **密级** | 内部 |
| **适用范围** | Delua 项目全生命周期 |

---

## 目录

- [第 1 章 安全原则与威胁模型](#第-1-章-安全原则与威胁模型)
- [第 2 章 供应链安全清单](#第-2-章-供应链安全清单)
- [第 3 章 第三方依赖安全审计](#第-3-章-第三方依赖安全审计)
- [第 4 章 闭源组件移除验证程序](#第-4-章-闭源组件移除验证程序)
- [第 5 章 Android 平台安全基线](#第-5-章-android-平台安全基线)
- [第 6 章 AndroidManifest 安全硬化](#第-6-章-androidmanifest-安全硬化)
- [第 7 章 权限最小化原则](#第-7-章-权限最小化原则)
- [第 8 章 数据存储与隐私保护](#第-8-章-数据存储与隐私保护)
- [第 9 章 网络通信安全](#第-9-章-网络通信安全)
- [第 10 章 原生代码（NDK）安全](#第-10-章-原生代码ndk安全)
- [第 11 章 Lua 引擎安全沙箱](#第-11-章-lua-引擎安全沙箱)
- [第 12 章 加密与密钥管理](#第-12-章-加密与密钥管理)
- [第 13 章 动态代码生成安全（DexMaker / CGLIB）](#第-13-章-动态代码生成安全dexmaker--cglib)
- [第 14 章 构建与 CI/CD 安全](#第-14-章-构建与-cicd-安全)
- [第 15 章 代码签名与发布安全](#第-15-章-代码签名与发布安全)
- [第 16 章 安全测试清单](#第-16-章-安全测试清单)
- [第 17 章 安全事件响应预案](#第-17-章-安全事件响应预案)
- [附录 A 安全检查对照表](#附录-a-安全检查对照表)
- [附录 B 术语表](#附录-b-术语表)

---

## 第 1 章 安全原则与威胁模型

### 1.1 设计原则

| # | 原则 | 说明 |
|---|---|---|
| **P1** | 最小权限 | 每个组件仅拥有完成其功能所需的最小权限集 |
| **P2** | 纵深防御 | 多层安全控制，单点失效不导致全系统沦陷 |
| **P3** | 安全默认 | 默认配置为最安全配置 |
| **P4** | 完全中介 | 对所有资源访问进行身份验证和授权检查 |
| **P5** | 开放设计 | 安全不依赖于实现的隐秘性（Kerckhoffs 原则） |
| **P6** | 最小通用机制 | 减少共享机制以降低信息泄露风险 |
| **P7** | 心理可接受 | 安全机制不应过度增加用户操作负担 |

### 1.2 威胁模型

```
┌─────────────────────────────────────────────────────┐
│                  威胁面分析                           │
├───────────────┬─────────────────────────────────────┤
│   威胁面       │   描述                               │
├───────────────┼─────────────────────────────────────┤
│ T1: 供应链攻击 │ 恶意依赖库、被篡改的 JAR/AAR 包        │
│ T2: 信息泄露   │ Lua 脚本访问未授权文件/数据            │
│ T3: 代码注入   │ 恶意 Lua 脚本利用 Java 桥接执行系统命令 │
│ T4: 权限提升   │ 通过 Lua 运行时获得超越宿主权限          │
│ T5: 隐私追踪   │ 闭源 SDK（百度等）收集用户数据           │
│ T6: 逆向工程   │ APK 反编译后提取敏感信息或密钥           │
│ T7: 原生漏洞   │ JNI 代码中的缓冲区溢出 / UAF 漏洞       │
│ T8: 动态生成   │ DexMaker 生成的代码被利用执行恶意负载    │
│ T9: 服务滥用   │ AccessibilityService 被用于键盘记录     │
│ T10: 降级攻击  │ 攻击者诱导用户降级到有漏洞版本            │
└───────────────┴─────────────────────────────────────┘
```

### 1.3 资产分级

| 分级 | 定义 | 示例 |
|---|---|---|
| **C1-关键** | 泄露导致严重用户损害 | 用户密钥、敏感文件、设备管理权限 |
| **C2-重要** | 泄露导致隐私暴露 | 联系人、位置、短信、照片 |
| **C3-普通** | 泄露影响有限 | 应用设置、缓存数据 |
| **C4-公开** | 无保密需求 | 内置 Lua 标准库、文档 |

---

## 第 2 章 供应链安全清单

### 2.1 依赖来源验证

```
必须检查：依赖来源完整性
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

| # | 检查项 | 验证方法 | 验收标准 |
|---|---|---|---|
| SS-01 | 所有 Maven 依赖使用 HTTPS 仓库 | 检查 `settings.gradle[.kts]` 和 `build.gradle[.kts]` | 无 HTTP 明文仓库 URL |
| SS-02 | 校验依赖校验和（checksum） | 启用 Gradle `dependency verification` | `gradle/verification-metadata.xml` 存在且完整 |
| SS-03 | AndroidX 依赖版本锁定 | 确认 `libs.versions.toml` 中所有版本非动态范围（如 `1.+`） | 所有版本为固定版本号 |
| SS-04 | 移除未使用的依赖 | `./gradlew buildHealth` 或手动审计 | 无僵尸依赖 |
| SS-05 | 审查新增的每个 Gradle 插件 | 记录插件来源、许可证、维护状态 | 所有插件来自可信发布者 |
| SS-06 | NDK 原生库来源审计 | 确认非预编译 .so，必须从 C 源码构建 | 每个 .so 对应完整 C 源码 |
| SS-07 | 检查 Gradle Wrapper 校验和 | 对比 `gradle/wrapper/gradle-wrapper.properties` | 使用官方 SHA-256 校验和 |
| SS-08 | 启用 Gradle 构建扫描（可选） | 集成 `com.gradle.enterprise` 插件 | 构建可追溯 |

### 2.2 依赖来源白名单

```
Gradle 仓库白名单（仅以下来源）：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ google()                          # Google Maven
✅ mavenCentral()                    # Maven Central
✅ gradlePluginPortal()              # Gradle 插件门户
❌ jcenter()                         # 已废弃，禁止使用
❌ 自定义 Maven 仓库（未审计的）      # 禁止使用
❌ flatDir 本地目录                  # 仅用于迁移过渡期

Maven Central 验证要求：
- 从 downloads.apache.org 下载（Apache 项目）
- 从 repo1.maven.org/maven2 直接引用（标准 Maven Central）
- 校验 PGP 签名和 SHA-256
```

### 2.3 Gradle 依赖验证配置

```kotlin
// gradle/verification-metadata.xml 必须包含以下配置：
// 1. 所有直接依赖的 checksum
// 2. 所有传递依赖的 checksum
// 3. 所有 Gradle 插件的 checksum

// 生成命令：
// ./gradlew --write-verification-metadata sha256
// ./gradlew --write-verification-metadata pgp,sha256
```

---

## 第 3 章 第三方依赖安全审计

### 3.1 现有依赖安全风险矩阵

| 依赖 | 版本 | 已知 CVE | 风险等级 | 行动 |
|---|---|---|---|---|
| AndroidX Core | 1.12+ | 无 | 🟢 低 | 保留 |
| AndroidX AppCompat | 1.6+ | 无 | 🟢 低 | 保留 |
| Material | 1.11+ | 无 | 🟢 低 | 保留 |
| Glide（替换为 Gradle 依赖） | 4.16+ | 部分旧版本有 CVE | 🟡 中 | 必须升级到最新补丁版 |
| Guava Android | 31.1+ | 旧版本有 CVE | 🟡 中 | 确认版本 ≥ 32.0.1 |
| LuaCustRecyclerAdapter（闭源） | 未知 | 未知 | 🔴 高 | 源码审查后替换 |
| Baidu MobStat（闭源） | 未知 | 已知含隐私争议 | 🔴 极高 | **必须移除** |
| BeanShell（闭源） | 未知 | 旧版本含 RCE 风险 | 🔴 高 | **必须移除** |
| `glide-full-4.7.0(AndroidX).jar` | 4.7.0 | CVE-2023-44387 等 | 🟡 中 | 替换为官方 Gradle 依赖 |
| `sign.dex` / `mao.dex` | 未知 | 未知 | 🔴 高 | **必须移除** |

### 3.2 依赖漏洞扫描

```
实施方式：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. OWASP Dependency-Check 集成（推荐）
   plugins {
       id 'org.owasp.dependencycheck' version 'X.X.X'
   }
   dependencyCheck {
       failBuildOnCVSS = 7
       suppressionFile = 'config/owasp-suppressions.xml'
   }

2. GitHub Dependabot（如使用 GitHub）
   - 启用 Dependabot alerts
   - 启用 Dependabot security updates
   - 为 Gradle 配置 dependabot.yml

3. 定期手动审计
   - 每月检查 https://nvd.nist.gov 最新 Android 相关 CVE
   - 每季度升级所有依赖到最新稳定版
```

### 3.3 传递依赖安全

```kotlin
// build.gradle.kts 配置示例：全局排除已知问题传递依赖
configurations.all {
    // 示例：排除旧版 Guava 中的已知漏洞
    // exclude group: "com.google.guava", module: "guava-jdk5"
    
    resolutionStrategy {
        // 强制使用修复 CVE 的版本
        force("com.google.guava:guava:32.1.3-android")
        force("com.squareup.okio:okio:3.6.0")
    }
}
```

---

## 第 4 章 闭源组件移除验证程序

### 4.1 移除前审计

```
闭源组件清单（必须逐项确认并记录）：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

| # | 组件 | 来源 | 许可证状态 | 移除理由 | 移除方式 | 验证人 |
|---|---|---|---|---|---|---|
| R-01 | `AndroLua+5.0.18_AndroidX.jar` | 未知（预编译） | 无开源许可证 | 核心混淆闭源 | 替换为 Pro 源码 | — |
| R-02 | `com.baidu.mobstat.*` | Baidu | 专有软件，闭源 | 用户隐私追踪 | 从源码和 JAR 中删除 | — |
| R-03 | `bsh.*` | BeanShell | LGPL | 无使用引用，含 ASM | 删除 | — |
| R-04 | `sign.dex` | 未知 | 未知 | 来源不明的 DEX 二进制 | 删除 | — |
| R-05 | `mao.dex` | 未知 | 未知 | 来源不明的 DEX 二进制 | 删除 | — |
| R-06 | `LuaCustRecyclerAdapter.jar` | 未知 | 无开源许可证 | 闭源二进制 | 审查后选择替换 | — |
| R-07 | `glide-full-4.7.0(AndroidX).jar` | 修改版 | 含闭源修改 | 使用官方 Glide 替代 | 替换为 Gradle 依赖 | — |
| R-08 | `libcrash_analysis.so` (armeabi) | 未知 | 未知 | 无对应 C 源码 | 删除 | — |

### 4.2 移除后验证协议

```bash
# 验证步骤（每个闭源组件移除后必须执行）：

# 1. 确认文件已物理删除
find . -path ./gradle -prune -o -name "AndroLua*.jar" -print
find . -path ./gradle -prune -o -name "*.dex" -print
find . -path ./gradle -prune -o -name "*baidu*" -print
find . -path ./gradle -prune -o -name "*bsh*" -print
find . -path ./gradle -prune -o -name "*crash_analysis*" -print

# 2. 确认无引用残留
grep -r "baidu" --include="*.java" --include="*.kt" --include="*.xml" --include="*.pro" .
grep -r "bsh" --include="*.java" --include="*.kt" .
grep -r "BeanShell" --include="*.java" --include="*.kt" .

# 3. 确认代码中无混淆类名
grep -rP "^(import |package )" --include="*.java" | grep -E "\.(a|b|c)\b" 

# 4. 仅依赖明确的 Open Source License：
#    - Apache 2.0
#    - MIT
#    - BSD 2/3-Clause
#    - LGPL 2.1/3.0
#    - MPL 2.0
#    - GPL 2.0/3.0（仅在项目同样使用 GPL 时）
```

### 4.3 许可证合规要求

| 许可证 | 兼容性 | 要求 |
|---|---|---|
| Apache 2.0 | ✅ 兼容 | 保留版权声明 |
| MIT | ✅ 兼容 | 保留版权声明 |
| BSD 2/3-Clause | ✅ 兼容 | 保留版权声明 |
| LGPL 2.1/3.0 | ✅ 兼容（动态链接） | 用户必须能替换库 |
| MPL 2.0 | ✅ 兼容 | 保留版权，修改部分开源 |
| GPL 3.0 | ⚠️ 需要项目同为 GPL | **不建议**引入 |
| AGPL 3.0 | ❌ 不兼容 | **禁止**引入 |
| 专有软件/无许可证 | ❌ 不兼容 | **禁止**引入 |

---

## 第 5 章 Android 平台安全基线

### 5.1 targetSdk 要求

```
最低 targetSdk 要求：33（Android 13）
推荐 targetSdk：34（Android 14）
目标 targetSdk：35（迁移完成后升级）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

| API 级别 | 安全要求 | 影响 |
|---|---|---|
| **targetSdk ≥ 31** | 广播接收器必须导出声明 | 隐式广播限制 |
| **targetSdk ≥ 33** | 通知运行时权限（POST_NOTIFICATIONS） | 通知需用户授权 |
| **targetSdk ≥ 33** | 前台服务类型声明 | 必须指定 `foregroundServiceType` |
| **targetSdk ≥ 34** | 隐式 Intent 限制收紧 | 仅对导出的组件有效 |
| **targetSdk ≥ 34** | 后台位置访问严格限制 | 必须有充分理由 |

### 5.2 compileSdk 与 buildTools

```
compileSdk = 34   # 不变
minSdk     = 21   # 不变（保留 Android 5.0 兼容）
targetSdk  = 34   # 从 29 升级到 34

Android 12+ (API 31+) 行为变更影响分析：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

| Android 版本 | 变更 | 对 Delua 的影响 |
|---|---|---|
| 12 (API 31) | `PendingIntent` mutability flag | `FLAG_IMMUTABLE` / `FLAG_MUTABLE` 必须指定 |
| 12 (API 31) | 精确闹钟权限（`USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM`） | 影响 LuaTimer |
| 12 (API 31) | 前台服务启动限制 | 影响 LuaService |
| 13 (API 33) | 通知运行时权限 | 需要适配新权限模型 |
| 13 (API 33) | 剪贴板内容预览限制 | 影响 TextWarrior 粘贴 |
| 13 (API 33) | 附近 Wi-Fi 设备权限（`NEARBY_WIFI_DEVICES`） | 影响 Http 检查 Wi-Fi |
| 14 (API 34) | 隐式 Intent 和导出限制 | 后台服务 Intent 需显式声明 |
| 14 (API 34) | 通知权限运行时分离 | NotificationCompat 变更 |
| 14 (API 34) | 屏幕截图检测 | 影响 LuaActivity 的截屏行为 |

### 5.3 后台执行限制

```
Android 12+ 后台优化要求：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

| 组件 | 受影响功能 | 适配方案 |
|---|---|---|
| `LuaService` | 后台长期运行 Lua 脚本 | 使用 `startForegroundService()` + 通知；配置 `foregroundServiceType` |
| `LuaTimer` / `LuaTicker` | 定时任务 | API 31+ 使用 `SCHEDULE_EXACT_ALARM` 权限 |
| `LuaThread` | 后台线程 | 遵守后台执行限制，超时后系统可能杀死 |
| `LuaAccessibilityService` | 无障碍自动化 | AccessibilityService 不受后台限制影响 |

---

## 第 6 章 AndroidManifest 安全硬化

### 6.1 导出组件审计

```
原则：所有组件必须显式声明 android:exported
未声明 exported 的组件在 API 31+ 将抛出 RuntimeException
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

```xml
<!-- ✅ 正确：导出的 Activity -->
<activity
    android:name="com.androlua.Welcome"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>

<!-- ✅ 正确：不导出的 Service -->
<service
    android:name="com.androlua.LuaService"
    android:enabled="true"
    android:exported="false" />      <!-- 必须显式声明 -->

<!-- ✅ 正确：导出的 AccessibilityService -->
<service
    android:name="com.androlua.LuaAccessibilityService"
    android:exported="true"           <!-- 必须显式声明 -->
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
</service>
```

### 6.2 删除无用组件

```xml
<!-- ❌ 删除：百度异常日志收集 -->
<!-- 从 AndroidManifest.xml 中删除 -->
<meta-data
    android:name="BaiduMobAd_EXCEPTION_LOG"
    android:value="true" />

<!-- ❌ 删除：华为 EasyGo 服务 -->
<meta-data
    android:name="EasyGoClient"
    android:value="true" />
```

### 6.3 FileProvider 安全配置

```xml
<!-- ✅ 正确：FileProvider 配置 -->
<provider
    android:name="android.content.FileProvider"
    android:authorities="${applicationId}"
    android:exported="false"            <!-- 必须为 false -->
    android:grantUriPermissions="true">

    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/androlua_filepaths" />
</provider>
```

```xml
<!-- 安全要求：androlua_filepaths.xml 必须限制路径范围 -->
<!-- ❌ 不要使用 <external-path name="root" path="/" /> -->
<!-- ✅ 应限制为应用特定目录 -->
<paths>
    <external-path
        name="lua_projects"
        path="AndroLua/" />
    <!-- 仅暴露所需最小路径 -->
</paths>
```

### 6.4 usesCleartextTraffic 安全评估

```xml
<!-- 当前值 -->
android:usesCleartextTraffic="true"

<!-- 安全建议：
     - 支持 HTTP 是 AndroLua 的功能需求（用户开发的 Lua 脚本可能访问 HTTP 站点）
     - 不能直接设为 false 以保持向后兼容
     - 替代方案：使用 network_security_config.xml 精细控制
-->
```

```xml
<!-- 替代方案：res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- 仅允许调试时明文 -->
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">10.0.2.2</domain>    <!-- 模拟器本地 -->
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
    <!-- 发布版本禁止明文（用户可在 Lua 中控制） -->
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```

---

## 第 7 章 权限最小化原则

### 7.1 权限必要性审查

```
现状：AndroidManifest 中声明了 50+ 权限
目标：审计每个权限，移除不必要权限
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

| 权限 | 必要性 | 理由 | 行动 |
|---|---|---|---|
| `INTERNET` | ✅ 必需 | 网络请求核心功能 | 保留 |
| `ACCESS_NETWORK_STATE` | ✅ 必需 | 网络状态检查 | 保留 |
| `ACCESS_WIFI_STATE` | ✅ 必需 | Wi-Fi 状态（Http 库） | 保留 |
| `WRITE_EXTERNAL_STORAGE` | ⚠️ 需评估 | AndroLua 文件管理 | API 29+ 改用 `MANAGE_EXTERNAL_STORAGE` 或 SAF |
| `READ_EXTERNAL_STORAGE` | ⚠️ 需评估 | 同上 | API 33+ 已弃用 |
| `CAMERA` | ✅ 功能必需 | LuaCameraView | 仅在需要时运行时申请 |
| `RECORD_AUDIO` | ✅ 功能必需 | 音频录制功能 | 运行时申请 |
| `ACCESS_FINE_LOCATION` | ⚠️ 功能可选 | 位置相关 Lua API | 标记为可选，运行时申请 |
| `ACCESS_COARSE_LOCATION` | ⚠️ 功能可选 | 同上 | 标记为可选 |
| `ACCESS_BACKGROUND_LOCATION` | ⚠️ 需严格评估 | 后台位置访问 | Android 10+ 需要单独申请 |
| `READ_PHONE_STATE` | ❌ 不必要 | 若只为获取 IMEI（隐私违规） | **移除** |
| `READ_CALL_LOG` | ❌ 不必要 | AndroLua 不需要通话记录 | **移除** |
| `WRITE_CALL_LOG` | ❌ 不必要 | 同上 | **移除** |
| `READ_SMS` | ❌ 不必要 | 非短信应用 | **移除** |
| `SEND_SMS` | ❌ 不必要 | 非短信应用 | **移除** |
| `READ_CONTACTS` | ❌ 不必要 | 非联系人管理 | **移除** |
| `WRITE_CONTACTS` | ❌ 不必要 | 同上 | **移除** |
| `READ_CALENDAR` | ❌ 不必要 | 非日历应用 | **移除** |
| `WRITE_CALENDAR` | ❌ 不必要 | 同上 | **移除** |
| `BODY_SENSORS` | ❌ 不必要 | 非健康应用 | **移除** |
| `ACTIVITY_RECOGNITION` | ❌ 不必要 | 非健身应用 | **移除** |
| `INSTALL_PACKAGES` | 🔴 高风险 | 允许安装 APK | 审计使用场景，**强烈建议移除** |
| `PERSISTENT_ACTIVITY` | ❌ 已废弃 | API 29+ 无效果 | **移除** |
| `BROADCAST_STICKY` | ❌ 已废弃 | API 21+ 已弃用 | **移除** |
| `SYSTEM_ALERT_WINDOW` | ⚠️ 功能相关 | FloatWindow 悬浮窗 | 保留，但需要运行时申请 |
| `REQUEST_INSTALL_PACKAGES` | ⚠️ 功能相关 | APK 安装功能 | 保留但需审计使用场景 |
| `BIND_ACCESSIBILITY_SERVICE` | ✅ 功能必需 | LuaAccessibilityService | 保留 |
| `FOREGROUND_SERVICE` | ✅ API 26+ 必需 | LuaService | 保留（Android 14 需声明类型） |
| `POST_NOTIFICATIONS` | ✅ API 33+ 必需 | 前台服务通知 | 运行时申请 |

### 7.2 运行时权限实施

```java
// 必须实现的运行时权限模式
// 所有危险权限（dangerous permission）必须：
// 1. 在使用时请求（而非安装时）
// 2. 提供 rationale（用户在拒绝后再次触发）
// 3. 处理永久拒绝场景（引导用户去设置）

// 示例：相机权限请求
public void requestCameraPermission(Activity activity, int requestCode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        return; // 旧版本自动授予
    }
    
    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
        
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.CAMERA)) {
            // 显示解释说明
            showRationaleDialog(() -> {
                ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.CAMERA}, requestCode);
            });
        } else {
            ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.CAMERA}, requestCode);
        }
    }
}
```

### 7.3 权限使用记录（Android 11+）

```
Android 11+ 要求：
- 位置、麦克风、相机权限在后台访问时记录
- 应用可以通过 PermissionController 查看
- 应用必须提供合理的使用说明

要求：在 AndroidManifest 中为位置权限添加 reason
<meta-data
    android:name="android.permission.ACCESS_BACKGROUND_LOCATION"
    android:resource="@string/background_location_reason" />
```

---

## 第 8 章 数据存储与隐私保护

### 8.1 数据存储类型与安全要求

| 数据类型 | 存储位置 | 安全要求 | 实现方案 |
|---|---|---|---|
| 用户 Lua 项目 | 外部存储 | 文件权限控制 | 使用 `Context.getExternalFilesDir()` |
| 应用设置 | SharedPreferences | 禁止存储敏感信息 | `MODE_PRIVATE` |
| 运行时缓存 | 内部缓存目录 | 定期清理 | `Context.getCacheDir()` |
| 日志输出 | logcat + 文件 | 禁止输出敏感信息 | 生产环境关闭调试日志 |
| 临时 DEX 文件 | 内部存储 | 使用后立即删除 | `LuaDexLoader` 确保清理 |
| 剪贴板内容 | 系统剪贴板 | API 33+ 自动屏蔽预览 | TextWarrior 适配 |
| Lua 脚本输出 | 用户指定 | 用户负责 | 文档说明风险 |

### 8.2 日志安全策略

```java
// 安全日志要求：
// 1. 日志级别区分：生产环境仅 ERROR
// 2. 禁止记录：密码、令牌、密钥、IMEI、位置信息
// 3. API key 和秘密必须通过 BuildConfig 注入

public class LuaPrint {
    private static final boolean DEBUG = BuildConfig.DEBUG;
    
    public static void d(String tag, String msg) {
        if (DEBUG) {
            Log.d(tag, sanitize(msg));
        }
    }
    
    // 日志清理函数：脱敏敏感数据
    private static String sanitize(String msg) {
        if (msg == null) return null;
        // 移除可能的密钥或令牌模式
        return msg.replaceAll(
            "(?i)(api[_-]?key|secret|token|password)=['\"]?[^&\\s'\"]+",
            "$1=***"
        );
    }
}
```

### 8.3 加密存储要求

```
敏感数据的存储必须加密：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

| 数据类型 | 存储方案 | Android 版本要求 |
|---|---|---|
| 用户密钥 | EncryptedSharedPreferences | API 23+ |
| 会话令牌 | 内存中持有，不持久化 | N/A |
| 本地数据库 | SQLCipher（加密 SQLite）或 Room + 加密 | 需引入相应依赖 |

---

## 第 9 章 网络通信安全

### 9.1 网络库安全要求

```java
// Http.java 迁移要求：
//
// ❌ 当前问题：
// - 使用 Apache HttpClient（已废弃）
// - 可能缺少 TLS 证书验证
// - 无超时控制
//
// ✅ 目标架构：
// - 使用 HttpURLConnection（内建）或 OkHttp（推荐）
// - 强制 HTTPS
// - 证书固定（Certificate Pinning）支持
// - 超时和重试策略
// - 遵循 Android 网络安全配置

// OkHttp 配置示例（如选择引入）：
public class SecureHttpClient {
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)   // 自动升级 HTTP → HTTPS
        .sslSocketFactory(createSslFactory(), createTrustManager())
        .hostnameVerifier((hostname, session) -> {
            // 生产环境：验证所有参数
            return HttpsURLConnection.getDefaultHostnameVerifier()
                .verify(hostname, session);
        })
        .build();
}
```

### 9.2 证书验证检查

```
TLS 证书验证要求：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ 必须验证：服务器证书链
✅ 必须验证：主机名匹配
✅ 必须验证：证书有效期
❌ 禁止：信任所有证书（ALLOW_ALL_HOSTNAME_VERIFIER）
❌ 禁止：空的 TrustManager
❌ 禁止：自定义 TrustManager 接受自签名证书（生产环境）

// 检查源代码中是否存在以下危险模式：
// check("HttpsURLConnection.setDefaultHostnameVerifier")
// check("TrustManager[] { new X509TrustManager")  
// check("checkClientTrusted = ... // empty")
```

### 9.3 WebView 安全配置

```java
// LuaWebView 安全硬化
public class LuaWebView extends WebView {
    private void configureSecurity() {
        // API 17+：防止 JavaScript 反射注入
        addJavascriptInterface(new LuaJavaScriptInterface(this), "Lua");
        
        // 禁用文件访问（除非必要）
        getSettings().setAllowFileAccess(false);
        getSettings().setAllowFileAccessFromFileURLs(false);
        getSettings().setAllowUniversalAccessFromFileURLs(false);
        
        // API 21+：启用混合内容阻止
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getSettings().setMixedContentMode(
                WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        
        // 禁用密码保存提醒
        getSettings().setSavePassword(false);
        
        // 安全评估：不应启用 JavaScript 自动执行
        // LuaWebView 是否真的需要 JS？若不需要则禁用
        // getSettings().setJavaScriptEnabled(false);
        
        // 禁用地理定位（除非需要）
        getSettings().setGeolocationEnabled(false);
    }
}
```

---

## 第 10 章 原生代码（NDK）安全

### 10.1 JNI 安全基线

```
JNI（Java Native Interface）是安全边界的关键节点
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

| 安全要求 | 说明 | 严重等级 |
|---|---|---|
| JNI 函数参数验证 | 所有从 Java 传入的参数必须在 C 端做 null 和边界检查 | 🔴 关键 |
| 缓冲区溢出防护 | 所有字符串/数组操作必须有长度检查 | 🔴 关键 |
| 返回值安全 | 返回给 Java 的指针引用必须有效 | 🔴 关键 |
| 异常处理 | JNI 调用 `ExceptionCheck()` 检查 Java 异常 | 🟡 重要 |
| 全局引用管理 | `NewGlobalRef` 必须有对应 `DeleteGlobalRef` | 🟡 重要 |
| 线程安全 | JNI 调用必须考虑多线程安全性 | 🟡 重要 |
| 本地引用表溢出 | 循环中及时 `DeleteLocalRef` | 🟢 建议 |

### 10.2 Lua C API 安全

```c
// Lua C API 安全清单

// 1. Lua 栈操作边界检查
// 每次 lua_settop / lua_push* / lua_to* 操作前确认栈空间
// lua_checkstack(L, extra) 检查返回值为 0 时处理

// 2. Lua 错误处理
// 所有可能出错的操作使用 lua_pcall（保护模式）
// 非 lua_pcall 必须设置错误处理函数

// 3. Lua 内存限制
// 使用 lua_newstate 的自定义 allocator 限制 Lua 内存使用
void* limited_alloc(void* ud, void* ptr, size_t osize, size_t nsize) {
    // 实现内存限制策略
    // 防止 Lua 脚本导致 OOM
}

// 4. Lua 执行限制
// 设置 debug hook 限制执行指令数
// 防止无限循环
lua_sethook(L, hook_func, LUA_MASKCOUNT, MAX_INSTRUCTIONS);

// 5. 安全环境
// 创建 Lua 沙箱环境
// 限制可用全局变量
// 禁用危险函数（dofile, loadfile, os.execute, io.* 等）
lua_newtable(L);              // 创建安全环境
lua_setglobal(L, "_ENV");     // 设置为 Lua 5.2+ 的环境
```

### 10.3 原生模块安全审计清单

| 模块 | 风险级别 | 审计重点 |
|---|---|---|
| `luajava` (JNI 桥接) | 🔴 高 | 栈操作、类型转换、Java 方法调用 |
| `lua` (解释器核心) | 🔴 高 | 词法分析、语法分析、字节码加载 |
| `luasocket` | 🟡 中 | 网络操作、DNS 查询、超时处理 |
| `cjson` | 🟡 中 | 内存分配、递归深度防止栈溢出 |
| `crypt` | 🔴 高 | 加密实现、密钥处理、旁路攻击 |
| `tcc` (嵌入式 C 编译器) | 🔴 极高 | 运行时编译带来的任意代码执行风险 |
| `zip` | 🟡 中 | 路径穿越、ZIP 炸弹 |
| `luagl` (OpenGL) | 🟢 低 | 图形 API，攻击面有限 |
| `canvas` | 🟢 低 | 位图处理，内存分配 |
| `regex` | 🟡 中 | ReDoS（正则拒绝服务） |

### 10.4 TCC（Tiny C Compiler）专项安全

```
⚠️ 注意：TCC 是本项目最高安全风险模块
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

TCC 允许在 Android 设备上运行时编译和执行 C 代码。
这带来了极大的安全风险：

风险：
1. 任意代码执行：Lua 脚本可以编译并运行任意 C 代码
2. 沙箱逃逸：C 代码可以直接访问系统调用
3. 持久化 rootkit：编译的代码可修改系统文件

缓解措施：
1. 默认禁用 TCC：仅在用户明确启用时加载
2. 编译时警告：明确提示用户正在编译 C 代码
3. 运行时限制：使用 seccomp-bpf 限制系统调用（API 21+）
4. 用户同意：首次使用需要用户确认风险
5. 文档警告：在产品文档中明确说明 TCC 的安全风险

// 实施示例：seccomp 限制
#include <linux/seccomp.h>
#include <linux/filter.h>

void restrict_syscalls() {
    struct sock_filter filter[] = {
        // 只允许基本系统调用
        // 禁止 execve、fork、ptrace 等高危调用
    };
    struct sock_fprog prog = {
        .len = sizeof(filter) / sizeof(filter[0]),
        .filter = filter,
    };
    prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0);
    prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &prog);
}
```

---

## 第 11 章 Lua 引擎安全沙箱

### 11.1 Lua 沙箱架构

```
目标：允许用户运行 Lua 脚本，但限制其访问系统资源和敏感操作
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

```lua
-- Lua 沙箱环境配置
local sandbox_env = {}

-- 白名单安全函数
sandbox_env.print = print
sandbox_env.pairs = pairs
sandbox_env.ipairs = ipairs
sandbox_env.tostring = tostring
sandbox_env.tonumber = tonumber
sandbox_env.type = type
sandbox_env.next = next
sandbox_env.select = select
sandbox_env.unpack = table.unpack or unpack
sandbox_env.error = error
sandbox_env.pcall = pcall
sandbox_env.xpcall = xpcall
sandbox_env.assert = assert
sandbox_env.setmetatable = setmetatable

-- 受限的标准库
sandbox_env.string = string       -- 保留只读函数
sandbox_env.table = table         -- 保留只读函数
sandbox_env.math = math           -- 纯计算，无危险
sandbox_env.os = {                -- 高度限制
    time = os.time,
    date = os.date,
    difftime = os.difftime,
    -- ❌ 不提供：execute, exit, rename, remove, tmpname
}
sandbox_env.io = nil              -- ❌ 完全禁用
sandbox_env.dofile = nil          -- ❌ 禁用
sandbox_env.loadfile = nil        -- ❌ 禁用
sandbox_env.load = nil            -- ❌ 禁用（可用修改版）
sandbox_env.require = nil         -- ❌ 禁用（可用修改版）
sandbox_env.module = nil          -- ❌ 禁用
sandbox_env.rawget = nil          -- ❌ 可能用于逃逸
sandbox_env.rawset = nil          -- ❌ 可能用于逃逸
sandbox_env.rawequal = nil        -- ❌ 可能用于逃逸
sandbox_env.collectgarbage = nil  -- ❌ 禁用
sandbox_env.newproxy = nil        -- ❌ 禁用
sandbox_env.coroutine = nil       -- ❌ 禁用（可能用于逃逸）
sandbox_env.debug = nil           -- ❌ 完全禁用

-- 安全检查：防止通过 metatable 逃逸
-- 设置 __metatable 保护
-- 防止 debug.getregistry 访问
```

### 11.2 Java 桥接安全控制

```java
// LuaJava 桥接 - Java 方法调用安全控制
public class LuaJavaAPI {
    
    // 白名单：允许 Lua 调用的 Java 包
    private static final Set<String> ALLOWED_PACKAGES = new HashSet<>(Arrays.asList(
        "com.androlua",
        "android.widget",
        "android.view",
        "android.graphics",
        "java.lang",
        "java.util",
        "android.app.AlertDialog"
        // ... 严格白名单
    ));
    
    // 黑名单：禁止反射调用的方法和类
    private static final Set<String> BLOCKED_CLASSES = new HashSet<>(Arrays.asList(
        "java.lang.Runtime",
        "java.lang.ProcessBuilder",
        "java.lang.reflect.Method",
        "java.lang.reflect.Field",
        "dalvik.system.DexClassLoader",
        "java.io.File"          // 通过 AndroLua API 替代
    ));
    
    // 安全检查函数
    public static Object checkJavaAccess(String className) {
        // 1. 检查白名单
        boolean allowed = ALLOWED_PACKAGES.stream()
            .anyMatch(className::startsWith);
        if (!allowed) {
            throw new SecurityException("Access denied: " + className);
        }
        
        // 2. 检查黑名单
        if (BLOCKED_CLASSES.contains(className)) {
            throw new SecurityException("Forbidden class: " + className);
        }
        
        // 3. 检查反射限制
        if (isReflectionClass(className)) {
            throw new SecurityException("Reflection not allowed: " + className);
        }
        
        return null;
    }
}
```

### 11.3 Lua 脚本执行限制

| 限制类型 | 实施方式 | Android API 级别 |
|---|---|---|
| 执行超时 | `lua_sethook` 设置指令数限制 | N/A（C 层实现） |
| 内存限制 | 自定义 `lua_Alloc` | N/A（C 层实现） |
| 递归深度 | Lua 内部 `CALL` 计数 | N/A |
| 表大小 | 运行时检查 | Java 层 |
| 字符串长度 | 限制最大字符串长度 | Java 层 |
| 并发执行 | 单 Lua State 串行化 | Java 层 synchronized |

### 11.4 安全事件日志

```java
// Lua 安全审计日志
// 所有以下操作必须记录安全日志：
// 1. Java 桥接调用失败（包名不在白名单）
// 2. 反射调用尝试
// 3. 高风险 Android API 调用（短信、电话、安装 APK）
// 4. 超过内存/时间限制
// 5. 沙箱逃逸尝试

// 安全日志必须不可篡改（仅追加）
// 日志文件存储在应用内部存储
// 用户可查看安全日志
```

---

## 第 12 章 加密与密钥管理

### 12.1 加密模块审计（`libcrypt.so`）

```c
// 加密模块安全要求

// 1. 随机数生成
// ✅ 使用：arc4random_buf (API 21+) 或 getrandom (API 28+)
// ❌ 避免：rand(), srand(), /dev/random (文件描述符可能耗尽)

// 2. AES 实现
// ✅ 使用：Android Keystore System (API 23+) 或 OpenSSL EVP
// ❌ 避免：自定义 AES 实现、ECB 模式

// 3. 哈希函数
// ✅ 推荐：SHA-256, SHA-3
// ⚠️ 避免：MD5 (已知碰撞攻击), SHA-1 (已知碰撞攻击)

// 4. 密钥处理
// 密钥在内存中使用后必须清零
// void secure_zero(void *ptr, size_t len) {
//     volatile unsigned char *p = ptr;
//     while (len--) *p++ = 0;
// }
```

### 12.2 Android Keystore 集成

```java
// 使用 Android Keystore 系统存储密钥
// API 23+ 支持硬件支持的密钥存储

@RequiresApi(api = Build.VERSION_CODES.M)
public class SecureKeyStore {
    private static final String KEY_ALIAS = "delua_master_key";
    
    public SecretKey getOrCreateKey(Context context) {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        
        if (keyStore.containsAlias(KEY_ALIAS)) {
            // 恢复现有密钥
            KeyStore.SecretKeyEntry entry = 
                (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
            return entry.getSecretKey();
        }
        
        // 创建新密钥
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)  // 需要生物识别
            .build();
        
        KeyGenerator generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(spec);
        return generator.generateKey();
    }
}
```

### 12.3 禁止的加密实践

```
禁止清单：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
❌ 硬编码密钥
   - 字符串常量中的密钥
   - BuildConfig 中的密钥（可被反编译读取）
   
❌ 不安全的加密模式
   - AES/ECB（电子密码本模式）
   - 无认证加密（AES/CBC 无 HMAC）
   - 固定 IV
   
❌ 弱密钥派生
   - 基于 MD5 的 PBKDF
   - 少于 10000 迭代的 PBKDF2
   - bcrypt 少于 10 轮
   
❌ 自定义加密算法
   - 任何自制的加密算法
   - 修改标准算法
   - XOR 作为加密手段
```

---

## 第 13 章 动态代码生成安全（DexMaker / CGLIB）

### 13.1 运行时 DEX 生成安全

```java
// DexMaker 运行时 DEX 生成 - 安全控制
// AndroLua 使用 DexMaker 动态生成 Java 代理类
// 这是正常功能所需，但需要安全控制

// 风险评估矩阵
// ┌──────────────┬──────────┬──────────────┬─────────────┐
// │ 场景         │ 风险等级 │ 影响         │ 缓解措施    │
// ├──────────────┼──────────┼──────────────┼─────────────┤
// │ Lua 接口实现 │ 🟢 低   │ 生成类实现    │ 受限于接口  │
// │              │          │ Java 接口     │ 定义         │
// ├──────────────┼──────────┼──────────────┼─────────────┤
// │ Enhancer     │ 🟡 中   │ 增强已有类    │ 检查被增强类│
// │ 类增强       │          │ 的方法       │ 是否在白名单│
// ├──────────────┼──────────┼──────────────┼─────────────┤
// │ 直接 DexMaker│ 🔴 高   │ 可生成任意    │ 禁止 Lua 直 │
// │ API 调用     │          │ DEX 字节码    │ 接访问       │
// └──────────────┴──────────┴──────────────┴─────────────┘

// 安全控制点
public class LuaEnhancer {
    // 增强类白名单
    private static final Set<Class<?>> ALLOWED_ENHANCE_TARGETS = 
        new HashSet<>(Arrays.asList(
            View.OnClickListener.class,
            View.OnLongClickListener.class,
            View.OnTouchListener.class,
            AdapterView.OnItemClickListener.class,
            // ... 仅添加已知安全的接口
        ));
    
    public Object enhance(Class<?> targetClass, LuaFunction callback) {
        if (!ALLOWED_ENHANCE_TARGETS.contains(targetClass)) {
            throw new SecurityException(
                "Enhancing " + targetClass.getName() + " is not allowed");
        }
        return Enhancer.create(targetClass, new LuaMethodInterceptor(callback));
    }
}
```

### 13.2 生成的 DEX 文件清理

```java
// 安全要求：动态生成的 DEX 文件使用后必须立即删除
// 防止内存耗尽或恶意代码残留

public class LuaDexLoader {
    private final File dexDir;
    
    public LuaDexLoader(Context context) {
        // 使用应用私有目录（非外部存储）
        this.dexDir = new File(context.getCacheDir(), "dex_cache");
        this.dexDir.mkdirs();
    }
    
    public Class<?> loadDex(byte[] dexData, String className) {
        File dexFile = new File(dexDir, className + ".dex");
        try {
            FileOutputStream fos = new FileOutputStream(dexFile);
            fos.write(dexData);
            fos.close();
            
            // 加载 DEX
            DexClassLoader loader = new DexClassLoader(
                dexFile.getAbsolutePath(),
                dexDir.getAbsolutePath(),
                null,
                getClass().getClassLoader()
            );
            
            Class<?> clazz = loader.loadClass(className);
            return clazz;
        } catch (Exception e) {
            throw new RuntimeException("DEX load failed", e);
        } finally {
            // 使用后立即删除（即使异常也执行）
            dexFile.delete();
        }
    }
}
```

---

## 第 14 章 构建与 CI/CD 安全

### 14.1 构建安全配置

```kotlin
// build.gradle.kts - 安全构建配置

android {
    // 混淆配置
    buildTypes {
        debug {
            isMinifyEnabled = false
            // 调试版本保留调试符号
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // 生产版本剥离调试信息
            // 防止通过 BuildConfig 泄露敏感信息
            buildConfigField("boolean", "ENABLE_DEBUG_LOGGING", "false")
            buildConfigField("String", "API_BASE_URL", "\"https://api.example.com\"")
        }
    }
    
    // NDK 符号剥离
    externalNativeBuild {
        ndkBuild {
            arguments "-DANDROID_STRIP=true"
            cFlags "-O2", "-fstack-protector-strong", "-D_FORTIFY_SOURCE=2"
        }
    }
}

// 签名配置
android {
    signingConfigs {
        release {
            // 使用环境变量注入敏感信息（永不硬编码）
            storeFile = file(System.getenv("DELUA_STORE_FILE"))
            storePassword = System.getenv("DELUA_STORE_PASSWORD")
            keyAlias = System.getenv("DELUA_KEY_ALIAS")
            keyPassword = System.getenv("DELUA_KEY_PASSWORD")
        }
    }
}
```

### 14.2 CI/CD 安全基线

```
CI/CD 安全要求：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

| 要求 | 说明 | 优先级 |
|---|---|---|
| CI 运行在隔离环境 | 使用独立的 CI runner，不使用共享 runner | 🔴 高 |
| 密钥不存储于仓库 | 所有签名密钥通过 CI secret 注入 | 🔴 高 |
| 构建产物签名验证 | 构建完成后自动验证 APK 签名 | 🟡 中 |
| 依赖扫描 | 每次构建运行 OWASP Dependency-Check | 🟡 中 |
| SAST 静态扫描 | 集成 Android Lint + FindBugs/SpotBugs | 🟡 中 |
| 构建不可变 | 同一提交每次构建产生相同产物（构建复现） | 🟢 建议 |

### 14.3 Android Lint 安全规则

```
必须显示配置的 lint 规则：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

```kotlin
android {
    lintOptions {
        isAbortOnError = true     // Lint 错误中断构建
        
        // 安全相关检查 - 必须通过
        disable += setOf(
            "MissingPermission"    // 运行时权限检查
        )
        enable += setOf(
            "HardcodedDebugMode",        // 禁止硬编码 debug 模式
            "LogConditional",            // 日志级别检查
            "UnprotectedSMSBroadcastReceiver",
            "UnpackedNativeCode",
            "TrustAllX509TrustManager",  // 🔴 禁止信任所有证书
            "SetJavaScriptEnabled",
            "SetTextImeSession",
            "ClickableViewAccessibility",
            "RtlEnabled",
            "ContentDescription",
            "SimpleDateFormat",
            "WorldReadableFiles",
            "WorldWriteableFiles",
            "UnusedAttribute",           // 潜在的安全配置遗漏
        )
    }
}
```

---

## 第 15 章 代码签名与发布安全

### 15.1 签名密钥管理

```
签名密钥管理要求：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

| 要求 | 实施方式 |
|---|---|
| 使用独立签名密钥 | 不得与其它应用共享密钥 |
| 密钥强度 | RSA 4096 位或 ECDSA P-384 |
| 密钥存储 | 硬件安全模块（HSM）或密钥管理服务 |
| 密钥访问控制 | 仅 CI 系统管理员有权访问 |
| 密钥轮换 | 每年轮换，旧密钥保留用于验证旧版本 |
| 备份 | 离线备份，物理安全存储 |
| 签名 V1/V2/V3 | 启用所有支持的签名方案 |

### 15.2 APK 完整性验证

```bash
# 构建后验证脚本
# verify_build.sh

echo "=== APK 完整性验证 ==="

# 1. 检查 APK 中是否包含闭源文件
unzip -l app/build/outputs/apk/release/app-release.apk | grep -E "\.(jar|dex)$" | grep -v "classes.*\.dex" 
if [ $? -eq 0 ]; then
    echo "❌ APK 包含意外的 .jar 或 .dex 文件"
    exit 1
fi

# 2. 检查 Baidu 残留
unzip -l app/build/outputs/apk/release/app-release.apk | grep "baidu"
if [ $? -eq 0 ]; then
    echo "❌ APK 包含 Baidu SDK"
    exit 1
fi

# 3. 验证签名
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk

# 4. 检查是否使用 V2/V3 签名
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk

# 5. 检查 minSdk / targetSdk
aapt dump badging app/build/outputs/apk/release/app-release.apk | grep -E "sdkVersion|targetSdkVersion"

echo "=== 验证完成 ==="
```

### 15.3 发布检查清单

```
发布前安全检查清单：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

| # | 检查项 | 完成 |
|---|---|---|
| RC-01 | APK 签名验证通过 | ☐ |
| RC-02 | 无调试日志输出（`BuildConfig.DEBUG = false`） | ☐ |
| RC-03 | ProGuard/R8 混淆已生效 | ☐ |
| RC-04 | 资源收缩已生效 | ☐ |
| RC-05 | 所有危险权限运行时申请 | ☐ |
| RC-06 | 无不必要的导出组件 | ☐ |
| RC-07 | `usesCleartextTraffic` 为 false 或精细控制 | ☐ |
| RC-08 | 依赖漏洞扫描通过 | ☐ |
| RC-09 | Android Lint 零错误通过 | ☐ |
| RC-10 | 单元测试、集成测试通过 | ☐ |
| RC-11 | 安全测试清单完成 | ☐ |
| RC-12 | 许可证合规审计通过 | ☐ |
| RC-13 | OWASP Mobile Top 10 评估完成 | ☐ |

---

## 第 16 章 安全测试清单

### 16.1 静态安全测试（SAST）

```
SAST 工具集成：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

| 工具 | 扫描范围 | 集成方式 |
|---|---|---|
| Android Lint | XML, Java, Kotlin, Gradle | 内建于 AGP |
| SpotBugs | Java 字节码 | Gradle 插件 |
| Detekt（如使用 Kotlin） | Kotlin 源码 | Gradle 插件 |
| MobSF CLI（可选） | APK 文件 | CI 集成 |
| OWASP Dependency-Check | 第三方依赖 | Gradle 插件 |

### 16.2 动态安全测试

```
测试场景与测试用例：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**安全测试用例：**

| 测试 ID | 测试场景 | 预期结果 | 测试工具 |
|---|---|---|---|
| ST-01 | Lua 脚本尝试调用 `Runtime.exec()` | 被安全拦截 | 手动/自动化 |
| ST-02 | Lua 脚本尝试反射加载未授权类 | 抛出 SecurityException | 单元测试 |
| ST-03 | Lua 脚本执行无限循环 | 触及时限被终止 | 手动 |
| ST-04 | Lua 脚本分配大量内存 | 触发内存限制 | 手动 |
| ST-05 | Lua 脚本尝试访问文件系统 | 被沙箱限制 | 手动 |
| ST-06 | 注入恶意 Lua 字节码 | 非法格式拒绝加载 | Fuzzing |
| ST-07 | AccessibilityService 滥用 | 仅用于用户授权场景 | 手动审查 |
| ST-08 | 网络请求 MITM 测试 | HTTPS 验证正确 | mitmproxy |
| ST-09 | APK 反编译分析 | 无硬编码密钥 | jadx, APKTool |
| ST-10 | 动态 DEX 生成逆向 | 生成的类受限于接口 | 手动 |
| ST-11 | 权限声明 vs 实际使用对比 | 无过度索权 | 权限审计 |
| ST-12 | WebView XSS 测试 | JS 注入被阻止 | 手动 |
| ST-13 | NFC/蓝牙功能未使用时的关闭 | 功能已禁用 | 代码审查 |
| ST-14 | libtcc 编译 C 代码后执行 | 沙箱限制生效 | 手动 |

### 16.3 渗透测试指南

```
针对 Delua 的渗透测试重点：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Lua 沙箱逃逸（最高优先级）
   - 尝试通过 metatable 漏洞逃逸（__index, __gc）
   - 尝试通过 coroutine 绕过
   - 尝试通过 debug 库（如果非完全禁用）
   - 尝试通过 Lua 重载（值类型混淆）

2. Java 桥接绕过
   - 尝试通过 Java 反射绕过白名单
   - 尝试通过 Serializable 机制执行代码
   - 尝试通过动态代理生成的类执行未授权操作

3. 原生层攻击
   - JNI 缓冲区溢出测试（长字符串、嵌套表）
   - Lua C API 模糊测试
   - TCC 模块的任意代码执行验证

4. 权限提升
   - 从 Lua 环境尝试安装 APK
   - 从 Lua 环境尝试修改系统设置
   - 从 Lua 环境绕过无障碍服务限制
```

---

## 第 17 章 安全事件响应预案

### 17.1 事件分级

| 级别 | 定义 | 响应时间 | 示例 |
|---|---|---|---|
| **S1-紧急** | 远程代码执行、用户数据大规模泄露 | 立即响应 < 4h | Lua 沙箱逃逸可执行系统命令 |
| **S2-高危** | 敏感权限滥用、信息泄露 | 24h 内响应 | 某权限被 Lua 脚本暗中利用 |
| **S3-中危** | 安全配置缺陷、已知漏洞 | 7 天内修复 | 依赖库 CVE，已有补丁 |
| **S4-低危** | 信息收集、文档更新 | 下个版本修复 | 日志记录过多信息 |

### 17.2 响应流程

```
S1/S2 安全事件响应流程：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. 发现与报告
   - 用户报告 / 自动监控告警
   - ASRS（Android Security Reward）通知
   - 内部安全审计发现

2. 评估与确认                 ← 30min
   - 重现漏洞
   - 评估受影响版本范围
   - 确定漏洞级别

3. 遏制                       ← 2h
   - 紧急修复：禁用受影响功能
   - 发布安全补丁（Hotfix）
   - 通知渠道公告

4. 根因分析                   ← 24h
   - 源码审查
   - 提交修复补丁
   - 安全回归测试

5. 修补与发布                 ← 48h
   - 发布包含安全修复的新版本
   - 更新安全公告
   - 对受影响用户推送更新

6. 事后复盘                   ← 7天
   - 安全事件分析报告
   - 改进安全流程
   - 更新威胁模型
```

### 17.3 安全公告模板

```markdown
---
标题:   Delua 安全公告 DSA-YYYY-NNN
级别:   [紧急/高危/中危/低危]
日期:   YYYY-MM-DD
CVE ID: CVE-YYYY-NNNN（如适用）
---

## 影响

- Delua 版本 < X.Y.Z
- 受影响功能：[功能名称]
- 攻击者可通过 [攻击向量] 实现 [影响]

## 描述

[漏洞的详细技术描述]

## 修复建议

- 升级到 Delua X.Y.Z+
- 临时缓解措施：[如适用]

## 致谢

[报告者/发现者]

## 时间线

- YYYY-MM-DD：发现
- YYYY-MM-DD：确认
- YYYY-MM-DD：修复
- YYYY-MM-DD：发布更新

## 更多信息

- 安全联系方式：security@delua.dev
```

---

## 附录 A 安全检查对照表

### A.1 迁移前安全检查

| # | 检查项 | 状态 | 备注 |
|---|---|---|---|
| PRE-01 | 确认 AndroLuaX 基线版本 | ☐ | — |
| PRE-02 | 记录所有已有权限 | ☐ | — |
| PRE-03 | 记录所有已声明组件 | ☐ | — |
| PRE-04 | 备份完整项目 | ☐ | — |
| PRE-05 | 确认所有相关人员已签署 NDA | ☐ | — |

### A.2 迁移中安全检查

| # | 检查项 | 状态 | 备注 |
|---|---|---|---|
| MID-01 | 每次闭源移除后验证 | ☐ | — |
| MID-02 | 每次源码移植后编译验证 | ☐ | — |
| MID-03 | 依赖变更后扫描 | ☐ | — |
| MID-04 | 权限变更后审计 | ☐ | — |
| MID-05 | 每阶段结束安全自检 | ☐ | — |

### A.3 迁移后安全检查

| # | 检查项 | 状态 | 备注 |
|---|---|---|---|
| POST-01 | OWASP Mobile Top 10 评估 | ☐ | — |
| POST-02 | 静态代码扫描（SAST） | ☐ | — |
| POST-03 | 依赖漏洞扫描 | ☐ | — |
| POST-04 | 签名与完整性验证 | ☐ | — |
| POST-05 | 发布前安全检查清单 | ☐ | — |

---

## 附录 B 术语表

| 术语 | 定义 |
|---|---|
| **SAST** | Static Application Security Testing - 静态应用安全测试 |
| **DAST** | Dynamic Application Security Testing - 动态应用安全测试 |
| **SCA** | Software Composition Analysis - 软件组成分析 |
| **CVE** | Common Vulnerabilities and Exposures - 通用漏洞披露 |
| **CVSS** | Common Vulnerability Scoring System - 通用漏洞评分系统 |
| **OWASP** | Open Web Application Security Project - 开放式 Web 应用安全项目 |
| **MSTG** | Mobile Security Testing Guide - 移动安全测试指南（OWASP） |
| **MASVS** | Mobile Application Security Verification Standard - 移动应用安全验证标准 |
| **ML-KEM** | Module-Lattice-Based Key Encapsulation Mechanism - 后量子加密（NIST 标准） |
| **seccomp-bpf** | Secure Computing mode with BPF filters - Linux 内核安全系统调用过滤 |
| **Keystore** | Android 硬件支持的密钥存储系统 |
| **TLS Pinning** | 证书固定 - 防止中间人攻击的技术 |
| **RCE** | Remote Code Execution - 远程代码执行 |
| **DEX** | Dalvik Executable - Android 字节码文件格式 |
| **JNI** | Java Native Interface - Java 原生接口 |

---

## 文档控制

| 版本 | 日期 | 变更说明 | 作者 |
|---|---|---|---|
| 1.0 | 2026-07-28 | 初始版本 | Delua 安全团队 |

---

## 免责声明

本文档为 Delua 项目的安全迁移指南，旨在帮助项目团队识别和缓解安全风险。本文档中的建议不构成法律建议，具体实施应结合项目实际情况和合规要求。对于因未遵循本文档指南而导致的任何安全事件，项目团队不承担责任。

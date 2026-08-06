# LIDE 插件平台（Lide Plugin Platform）架构设计文档

| 项目 | 内容 |
|------|------|
| 文档编号 | LIDE-DESIGN-2026-001 |
| 文档类型 | 架构设计 / 方案定稿（待按 §16 分阶段实施） |
| 编写日期 | 2026-08-06 |
| 编写依据 | 立项沟通纪要（2026-08-06） + 审计 LIDE-AUDIT-2026-014（见 §19.2） |
| 验收标准 | 本《§15 测试与验收》（含可执行命令锚点），对标 Apple/Google 平台级组件验收 |
| 概况 | LIDE「小政府」化：只做平台、契约、准入、生命周期；插件（含 androlua）自行实现全部业务与 UI；开放接入（技术上被识别即可用，类 Xposed 模型） |

> 文档生命周期：本文档全部 Phase 完成后按 `AGENTS.md` 文档治理约定 `git mv` 至 `docs/discard/`，
> 进度在 `docs/kb/README.md` §2 矩阵随阶段标记。

---

## 目录

1. [执行摘要](#1-执行摘要)
2. [需求与设计目标](#2-需求与设计目标)
3. [设计原则](#3-设计原则)
4. [总体架构](#4-总体架构)
5. [契约层：Plugin SDK](#5-契约层plugin-sdk)
6. [插件注册与发现](#6-插件注册与发现)
7. [连接生命周期状态机](#7-连接生命周期状态机)
8. [安全与信任模型](#8-安全与信任模型)
9. [页面契约与导航](#9-页面契约与导航)
10. [能力模型与事件通道](#10-能力模型与事件通道)
11. [宿主数据面：工程存储与运行锁](#11-宿主数据面工程存储与运行锁)
12. [androlua 插件实例化](#12-androlua-插件实例化)
13. [宿主（LIDE）改造清单](#13-宿主lide改造清单)
14. [模块、构建与签名](#14-模块构建与签名)
15. [测试与验收要求](#15-测试与验收要求)
16. [实施路线图](#16-实施路线图)
17. [风险与缓解](#17-风险与缓解)
18. [文档与治理动作](#18-文档与治理动作)
19. [附录](#19-附录)

---

## 1. 执行摘要

- **定位反转**：LIDE 由「内嵌 AndroLua 引擎」改为「平台宿主（小政府）」，androlua 成为其首个第三方插件。
- **开放政策（核心承诺）**：LIDE 对外发布稳定 Plugin SDK。任何在技术上满足注册声明的 Android APK
  都能作为插件被识别和使用——**默认不设厂商白名单**（与 Xposed 模块生态等价的技术识别模型），
  并提供「受信证书白名单」作为可选加固开关（默认关闭）。
- **技术选型**：采用 **GMS 式服务契约**（声明式注册 + `bindService` + 类型化 AIDL），
  而非 Xposed 式的同进程注入（LIDE 需要进程级物理隔离）。二者共享「无桌面图标 + manifest 声明」形态，
  但运行模型完全不同。
- **UI 边界**：插件 UI 全部归插件自实现；LIDE 只提供页面注册表 + 类型化导航 + 返回/结果通道。
- **三大历史枢纽闭环（前审计 §6）**：
  - 打包链 → 迁移为插件内 `build` 页面（§12）；
  - API 浏览器数据源 → 插件经 `api.index` 能力下发索引快照（§10）；
  - 工程目录 → 共享目录 + 运行锁 + 插件权威（§11）。

---

## 2. 需求与设计目标

### 2.1 Goals（必须交付）

| # | 目标 | 验收锚点 |
|---|------|----------|
| G1 | 宿主纯净：LIDE 不感知任何具体插件 | `rg` 门禁零命中；dex 无 androlua/luajava 类（§15-1/2） |
| G2 | 公开稳定的 Plugin SDK | SDK 独立模块、版本化、冻结 v1 |
| G3 | 开放生态：声明即识别 | 第三方冒烟插件仅凭 manifest 即可被识别启用（§15-6） |
| G4 | 进程级失败隔离 | 插件崩溃/被 kill 不影响宿主编辑（§15-4） |
| G5 | 插件 UI 自实现、经 LIDE 入口进入 | 无桌面图标的插件也可经插件中心进入（§15-3） |
| G6 | 能力不设上限 | 终端 / 调试 / 打包 / Git / AI… 均可扩展（§10） |

### 2.2 Non-Goals（一期不做）

| # | 不做 | 原因 / 替代 |
|---|------|------------|
| N1 | 宿主侧任何插件业务 UI | 归插件；宿主零执行业务 |
| N2 | 宿主加载插件 dex / 同进程注入 | 违反 G4；需进程隔离 |
| N3 | 用 scheme / Intent extra 传业务数据 | 一律类型化 AIDL/DTO（§5/§10） |
| N4 | Xposed / LSPosed 通道 | root / 注入，非官方 |
| N5 | SCVH 内嵌页面增强 | 一期不作 P0；v2 预留 |
| N6 | 插件商店 / 计费 / 分发 | 一期仅骨架 |
| N7 | 宿主为插件写缺省业务配置 | 数据与配置归插件 |

---

## 3. 设计原则

| 原则 | 内容 |
|------|------|
| P1 | 契约即主真相：LIDE 对插件的一切认知来自 Plugin SDK 契约，无插件 id 硬编码 |
| P2 | 技术识别开放：满足注册即被发现；白名单为可选开关（默认关） |
| P3 | 数据面类型化：业务数据一律 AIDL 强 DTO，禁止字符串/JSON 手拼 |
| P4 | 可替换：契约描述「什么」而非「谁」，引擎实现可更换 |
| P5 | 可验证门禁：R1/R5 延伸至 `:app` 宿主零引擎符号 |
| P6 | 可降级：插件缺位时宿主降级「纯编辑器」且不崩溃 |

---

## 4. 总体架构

```text
┌─────────────────────────────── LIDE 宿主（小政府）──────────────────────────────┐
│  :app（LIDE UI）                                                                 │
│   ├─ PluginCenter 插件中心（列表 / 状态 / 启用 / 入口 / 引导）                      │
│   ├─ PluginRegistry 注册表（queries 扫描 + descriptor 准入）                       │
│   ├─ ConnectionLifetime 生命周期（bindService / linkToDeath / 恢复）              │
│   ├─ Navigator 页面导航（pluginUiContract 封装）                                   │
│   └─ ApiIndexAdapter（API 浏览器数据源代理，优先插件快照）                          │
│                                                                                  │
│   发现：io.lidex.plugin.PLUGIN_PROVIDER action（仅发现 tag，无业务数据）            │
│   绑定：bindService + AIDL 类型化契约                                              │
│   数据：IBinder capability · typed Parcelable（不用 scheme / 裸 Intent）           │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                ▼
┌─────────────────────────────── 插件 APK（任意厂商）────────────────────────────┐
│   无 LAUNCHER（可选暴露桌面入口）                                                 │
│   Manifest：<service PluginProviderService exported> + 注册 meta-data            │
│   AbstractPluginProviderService：getDescriptor / componentForPage / getCapability│
│   插件自实现：页面 Activity（工程信息/权限/构建/运行）· 能力实现 · 业务数据         │
└──────────────────────────────────────────────────────────────────────────────────┘
        id=com.lioisme.lide.androlua（实例一） × 任意其他插件
```

宿主（小政府）职责分类：

| 职责 | 实现面 | 不属于宿主 |
|------|--------|-----------|
| 发布规则与注册表 | Plugin SDK + Registry | 不执行业务 |
| 准入校验（技术识别） | queries + descriptor 校验 | 默认无白名单 |
| 生命周期管理 | Connection/状态机 | 不杀、不注入 |
| 页面导航与返回 | Navigator + pageContainer | 不渲染插件内容 |
| 数据通道 | 类型化 RPC/事件 | 不构造业务数据 |
| 三态引导 | PluginCenter UI | 不替插件升级 |

---

## 5. 契约层：Plugin SDK

### 5.1 模块形态

- 新 Gradle 子模块 `:plugin-sdk`（库，纯接口 + DTO，无宿主业务 / 无引擎依赖）。
- 编译面：AIDL 接口 + Kotlin/Java 常量 + base 类，宿主与插件双端依赖。
- 发布：随仓库 tag 发布，版本号 = `apiLevel` 整型（自 1 起只增不改）。

### 5.2 描述类型（类型化 DTO）

```aidl
parcelable CapabilityDescriptor {
    String  id;            // 能力唯一 id，如 "engine.run"、"api.index"
    String  schema;        // 语义 schema，如 "lide-engine-run/v1"
    String[] dependencies; // 可选：上游能力
    int     minApiLevel;
}

parcelable PageDescriptor {
    String  id;            // 页面 id：project-info / permissions / build / run
    String  title;
    int     minApiLevel;
    boolean desktopEntry;  // 是否作为系统桌面入口（插件自定）
}

parcelable PluginDescriptor {
    String  pluginId;         // 逆向域名唯一 id
    String  displayName;
    String  vendor;
    String  version;
    int     apiLevel;         // 实现的契约版本
    CapabilityDescriptor[] capabilities;
    PageDescriptor[] pages;
}
```

### 5.3 核心 AIDL（契约 v1）

```aidl
// —— 插件实现 ——
interface IPluginProvider {
    PluginDescriptor getDescriptor();
    String componentForPage(in String pageId);      // 页面 → 组件全名（宿主零硬编码）
    IBinder getCapability(in String capabilityId);  // 返回强类型能力 IBinder
    void setHost(in IPluginHost host);              // 注入宿主回调
}

// —— 宿主回调 ——
interface IPluginHost {
    void onConnectionResult(int status, in String detail);
    void onEvent(in String eventType, in Parcelable payload); // 类型化事件 + 命名 DTO
}
```

### 5.4 版本协商

- 插件 apiLevel < 宿主最低支持 → 「需升级插件」
- 插件 apiLevel > 宿主 max → 「需升级宿主」，不绑定
- 兼容 → CONNECTED

---

## 6. 插件注册与发现

### 6.1 manifest 声明（SDK 提供模板）

```xml
<service android:name=".PluginProviderService" android:exported="true">
    <intent-filter>
        <action android:name="io.lidex.plugin.PLUGIN_PROVIDER" />  <!-- 发现 tag，非数据 -->
    </intent-filter>
    <meta-data android:name="io.lidex.plugin.ID"  android:value="com.example.plugin"/>
    <meta-data android:name="io.lidex.plugin.API  android:value="1"/>
</service>

<queries>
    <intent><action android:name="io.lidex.plugin.PLUGIN_PROVIDER" /></intent>
</queries>
```

- action 仅作「我是 LIDE 插件」的发现标定，不承载业务数据。
- 插件可选声明 LAUNCHER：不声明即桌面不可见（合法）。

### 6.2 宿主发现流程

```
1. queryIntentServices(PROVIDER_ACTION, MATCH_ALL) → 候选集
2. 对候选读 meta-data → 命中 → 可发现集
3. bindService → IPluginProvider.getDescriptor() → 版本校验
4. 失败 → 「损坏/签名不符」标记
```

- 发现≠厂商准入：任何按规范声明的 APK 自动被识别（开放）。

---

## 7. 连接生命周期状态机

```text
UNKNOWN（未安装）→ DISCOVERED（已注册未启用）
  → ENABLED → CONNECTING（bind + 校验，10s 超时）
    → CONNECTED（运行）
    → ERROR（bind 失败/拒绝）→ 重试
  CONNECTED → SUSPENDED（进程被回收，自动恢复重连）
            → DEAD（Binder 死亡/崩溃）→ 一键重启
```

| 状态 | 触发 | 宿主动作 |
|------|------|----------|
| UNKNOWN | 未装/卸载 | 显示安装引导 |
| DISCOVERED | 注册表可见 | 可启用 |
| CONNECTING | 用户启用 | bindService，UI 转态 |
| CONNECTED | bind+校验 | 读描述/页面/能力 |
| SUSPENDED | 进程回收 | 自动重连 |
| DEAD | Binder 死亡 | 「已崩溃」+ 重启 |

---

## 8. 安全与信任模型

| 策略 | 默认 | 说明 |
|------|------|------|
| 开放识别 | ON | 声明即绑定，无包名白名单 |
| 证书白名单（加固） | OFF | 管理员配置 SHA-256 清单，开启后仅名单内可绑定 |
| 来源透明 | 强制 | 插件中心展示包名/版本/厂商 |
| 插件侧自防护 | 插件 | 可加 signature 权限 |
| 数据保护 | 宿主最小 | 宿主不落地插件私有数据 |
| 崩溃隔离 | 系统级 | 独立进程，无权限共享 |

类比：Xposed 靠 root 注入，LIDE 靠系统级进程隔离 + 契约连接；「任何声明即识别」的开放哲学一致。

---

## 9. 页面契约与导航

- 页面 = 插件自实现 Activity（View/Compose 不限），由插件在自身模块内注册并实现。
- Navigator（SDK 封装）：`open(context, descriptor, pageId, Parcelable args)`：
  1. componentForPage → 显式 Intent（仅 UI 呈现通道）；
  2. 无桌面入口插件也能打开；
  3. handle requestToken，结果经 `onEvent("page.finished")` 回收；
  4. 返回插件中心恢复前状态。
- 参数/返回一律类型化 DTO，禁止字符串拼接或业务 scheme。

---

## 10. 能力模型与事件通道

- 类型化 RPC 集合，如 `engine.run`、`api.index`、`build`。
- 宿主调用：`provider.getCapability(id)` → 强转调用，对实现零感知。
- 事件：`pluginHost.onEvent(type, payload)` 分发给宿主 UI 订阅者。
- 依赖校验：能力不满足时禁入口 + 提示。

---

## 11. 宿主数据面：工程存储与运行锁

### 11.1 数据权威

- 工程数据真源归插件（sdcard 共享路径，pro 兼容）；宿主编辑器只读消费。

### 11.2 运行锁

- 插件运行前创建 `run.lock`；宿主编辑器在该工程进入只读提示。
- 释放：正常结束 / 进程死亡（DeathList）+ 超时续期；状态经 `engine.run` 查询。

### 11.3 API 浏览器数据源（枢纽）

- 能力 `api.index.getIndexSnapshot()` 返回类名集合（dexNames + 预索引）AIDL 下发。
- ApiIndexAdapter 优先插件快照；无插件时降级内置索引 + 工程 libs；
  无插件则提示「引擎插件未安装」。

---

## 12. androlua 插件实例化

| 资产 | 现状 | 去向 |
|------|------|------|
| 原生引擎 cpp/（NDK29） | `:app` | `:androlua` |
| 运行时 lua/* + bootstrap | `:app` | `:androlua`（assets） |
| com.androlua / com.luajava / com.nirenr | `:app` | `:androlua` |
| 编排 Kotlin（AlpManager / LuaCompiler / AXml / ApkBuilder / signer …） | `:app` | `:androlua` |
| EngineActivity / LuaActivity | `:app` | `:androlua`（run / build 页面） |
| 三页 UI（工程信息/权限/构建） | `:app` | `:androlua` pages |
| 宿主 `:app` | — | 编辑器 + PluginCenter |

androlua 插件形态：`applicationId=com.lioisme.lide.androlua`，无 LAUNCHER，实现 Plugin 契约。

工程目录 `sdcard/AndroLua` 物理路径不变，无缝迁移；引擎探针数据不依赖宿主类，兼容性不变。

---

## 13. 宿主（LIDE）改造清单

| 项 | 改动 |
|----|------|
| `LideApp` | 去除 `LuaApplication` 继承；移除 assets/lua bootstrap |
| 依赖 | 移除 `implementation(project(":androlua"))`；新增 `plugin-sdk` |
| EngineBridge / RunEngineLauncher | → Navigator（打开 androlua run 页面） |
| `LuaApplication.getInstance()` 等 15 处 | 收敛为 `WorkspaceFileAccess`（宿主只读写） |
| Build / ProjectInfo / Permission 三屏 | → 插件页面；宿主保留入口 + 删除实现 |
| API 浏览器数据源 | → ApiIndexAdapter（§11.3） |
| manifest | 删除 60+ 引擎权限；保留编辑所需 |

R 门禁（采纳后 AGENTS 二次修订执行）：

```powershell
rg -n "luajava|androlua|loadLibrary" app/src editor/src   # 宿主 app + editor 零命中
rg -n "de.robv.android.xposed" app/src                     # 不使用注入
```

---

## 14. 模块、构建与签名

```text
settings: :app · :plugin-sdk · :androlua(application) · :editor · :sora-editor(...)
```

| 变更 | 说明 |
|------|------|
| `:plugin-sdk` | 新库模块，AIDL+常量+base，minSdk 24 |
| `:androlua` | library→application；插件化 |
| ndk-build | 迁移至 `:androlua`；`:app` 删除 externalNativeBuild |
| 签名 | 双 APK 统一 keystore（CI） |
| 变体 | x64/arm64 dual flavor 保留 |
| 测试迁移 | 探针 878/956 及打包测试迁至插件模块 |
| 发布 | `:plugin-sdk` 产出 AAR 可发 Maven |

构建验证命令：

```powershell
.\gradlew.bat :app:assembleArm64Debug          # 宿主
.\gradlew.bat :androlua:assembleArm64Debug     # 插件（含 Native）
.\gradlew.bat :plugin-sdk:assembleArm64Debug   # SDK AAR
```

---

## 15. 测试与验收要求（可执行）

| # | 验收项 | 证据 | 通过标准 |
|---|--------|------|---------|
| 1 | 宿主纯净 | `rg -n "luajava\|androlua\|loadLibrary" app/src editor/src` | 零命中 |
| 2 | 宿主 dex 无引擎类 | apkanalyzer/dexdump | 无 androlua/luajava |
| 3 | 无桌面图标 | launcher 列表 | 插件不从桌面直接打开 |
| 4 | 崩溃隔离 | 杀插件进程后宿主 pid 不变、可编辑 | G4 |
| 5 | 状态恢复 | 再启动显示「已崩溃/恢复」 | - |
| 6 | 开放冒烟 | 非 LIDE 签名第三方插件仅凭 manifest 被识别 | G3 |
| 7 | 三态引导 | 未装/版本/损坏 | 正确提示 |
| 8 | 页面闭环 | 进 build/run → 返回回传 | 数据正确 |
| 9 | 探针回归 | 878/956 · demo 双端 | 持平基线 |
| 10 | R8 门禁 | 插件 release mapping | 全绿 |
| 11 | 性能 | 插件中心加载及首绑 < 2s | 通过 |

---

## 16. 实施路线图（Phase 0~5 · Exit Criteria）

| Phase | 交付 | 估算 | Exit |
|-------|------|------|------|
| 0 基线 | assemble+test 全绿；探针留存 | 0.5d | 现有回归通过 |
| 1 平台 v1 | `:plugin-sdk` + Registry/Connection/Navigator + PluginCenter 最小闭环 | 3-5d | 验收 1/4/6/11；Hello 插件 |
| 2 androlua 插件 | 引擎/资产/三页迁 `:androlua`；宿主收敛 | 5-10d | 验收 1/2/9/10 全绿 |
| 3 能力全量 | api.index、engine.run、runlock | 3-5d | 验收 5/8 通过 |
| 4 安全加固 | 信任白名单开/闭 | 2d | 双模式验证 |
| 5 治理收口 | AGENTS/DECISIONS/kb/TASKS/DESC 更新归档 | 1d | 全绿 + 文档齐 |

---

## 17. 风险与缓解

| # | 风险 | 缓解 |
| 1 | 插件缺位 → 运行/构建不可用 | 三态 + 降级纯编辑器 |
| 2 | 开放=不可信 | 可配白名单 + 证书/隔离/透明 |
| 3 | 插件进程频繁被杀 | 自动恢复 + 巡航接入 |
| 4 | AIDL 契约扩展 → 兼容问题 | apiLevel 冻结/协商 |
| 5 | 数据竞争（宿主/插件同一工程） | 运行锁 + 只读提示 |
| 6 | 双 APK 签名不一致 | CI 统一 keystore |
| 7 | 迁移回归 | 测试迁移 + 门禁 |

---

## 18. 文档与治理动作

1. AGENTS.md：R1 扩至 `:app`；R2 改「引擎在独立插件」；R4 注明 `:plugin-sdk` 契约域。
2. DECISIONS：登记 D-18（本方案；与 D-02/D-08 反向）。
3. kb/README §2 矩阵登记本文档（planned）。
4. TASKS 登记 Roadmap。
5. 原审计 LIDE-AUDIT-2026-014（双 APK）`git mv` 至 `docs/discard/`，标注被本文档替代。

---

## 19. 附录

### 19.1 术语

| 词 | 含义 |
|----|------|
| host | LIDE 宿主（小政府） |
| plugin | 独立 APK，实现 Plugin 契约 |
| capabilities | 类型化能力（engine.run / api.index / build） |
| apiLevel | SDK 契约版本（int） |
| PluginCenter | 宿主侧插件中心 |

### 19.2 证据引用

- LIDE-AUDIT-2026-014 双 APK 审计（已入 discard）。
- AGENTS.md 边界与验证命令。

### 19.3 修订历史

- 2026-08-06：初稿。
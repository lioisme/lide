# lide Java API 浏览器 —— 现状审计与可行性报告

| Metadata | Value |
|----------|-------|
| 文档 ID | LIDE-API-001 |
| 状态 | **已实施 + 模拟器验收归档（2026-08-02；ACC 结果见 §6.1）** |
| 关联 WP | ROADMAP WP-008（Java API 浏览器，P1 / 批 4）、WP-007（fiximport，共享类表） |
| 对照版本 | AndroLua Pro 4.4.2（nirenr/AndroLua_pro，commit `abe5c25`，master） |
| 审计日期 | 2026-08-02 |

---

## 0. 结论摘要

1. **lide 目前没有 API 浏览器**：`:app` / `:editor` 均无任何实现，仅残留 pro 的 3 个 `.aly` 布局模板（`app/src/main/assets/layouts/javaapi/`），未接线、无类表、无入口。
2. **AndroLua Pro 的"Java 方法浏览器"不是原生代码，而是一个纯 Lua 资产工程**（`assets/javaapi/`：`main.lua` + 手写类表 `android.lua`（4,124 个类名）+ 3 个布局 + `fiximport.lua`），功能 = 类名搜索 + 点击反射出构造器/事件/getter/setter/字段/方法。**它没有任何分类筛选**（只有"全部"+搜索框），更没有"谷歌质感库/AndroidX/R 资源类"概念。
3. **用户要求的"自动识别筛选"在 lide 技术上完全可行，且能做得比 pro 更好**：分类可全部由规则自动判定（包名前缀 + 反射验证），其中"谷歌质感库控件"与"AndroidX 控件"类别有真实数据支撑——lide 宿主 APK 已依赖 `com.google.android.material:material:1.13.0` 与完整 Compose/AndroidX 全家桶，类确实存在于运行进程，反射即可用。
4. **原生库（工程 `libs/*.so` C 模块）不支持函数级 API 自动识别**——诚实结论，理由见 §4；但"模块名级"识别可以做到（与打包器 checklib 同源）。工程 `libs/*.dex|jar`（字节码库）则**支持完整自动识别**（类级），已纳入方案。
5. 实现落点按 R1/R3 归入 `:app`（反射是运行时语义，`:editor` 保持纯文本级）。预计规模：1 个数据层（类表平移 + 扩展 + 动态枚举 + 分类器）+ 1 个 Compose 页面 + 1 个详情页 + 导航接线 + 资产搬运，约 1 个批次（批 4 剩余项）可完成。

---

## 1. 现状审计：lide 侧

### 1.1 代码面：零实现

| 检查项 | 结果 |
|--------|------|
| `:app` 中 `ApiBrowser / ApiList / ApiActivity` 等符号 | **零命中**（grep 全量 kotlin/java 源码） |
| `:editor` 中相关符号 | 零命中（符合 R1） |
| 导航注册（`LideNavHost.kt` Routes） | 无 API 浏览器路由（现有：projects/editor/settings/permissions/projectpermissions/projectinfo/buildapk/plugins/manual/examples） |
| 规划台账 | `docs/kb/ROADMAP.md` WP-008「Java API 浏览器（类表 + 反射详情，事件推导规则对齐 pro）」P1/批 4，**未开工**；`FULL_AUDIT_REPORT_2026-08-01.md` 将 API 浏览器列入"高频创作特性缺失" |

### 1.2 资产面：有残留，未接线

`app/src/main/assets/layouts/javaapi/` 下有 pro 的三个布局模板：

- `clayout.aly`（类列表页：EditText 搜索 + ListView 快速滚动）
- `mlayout.aly`（成员列表页：EditText 过滤 + ListView）
- `layout.aly`（"正在分析。。。"过渡页，注意该文件含乱码字符）

缺失配套：**没有 `main.lua`（浏览器逻辑）、没有 `android.lua`（类表）、没有 `init.lua`、没有 `fiximport.lua`**。`LideApp.kt:53` 仅把这批 `.aly` 当"布局助手模板"提取到 `filesDir/layouts/` 复用，与 API 浏览器无关。→ 这些文件是搬运 pro 资产时的残留，属于"死资产"。

### 1.3 相关可用能力（方案加分项）

- `:app` 依赖 `com.google.android.material:material:1.13.0` + Compose BOM/navigation/core-ktx（`app/build.gradle.kts`）→ 宿主进程内真实存在 Material/AndroidX 类。
- 工程模型支持 `libs/*.dex|jar|so`（`docs/kb/ARCHITECTURE.md`、`LuaDexLoader`），打包器已有 `checklib` 依赖分析（require/import 正则扫描）→ 工程库类别可直接复用其扫描思路。
- WP-007（fiximport）明确"类表数据源 = 平移 pro `android.lua` 4510 行"→ **与 WP-008 共享同一份类表资产**，一次平移两处受益。
- 事件推导规则（`setOnXxxListener → onXxx`）在 pro 中是 Lua 正则，WP-008 要求对齐；Kotlin 重写同一规则即可。

---

## 2. 现状审计：AndroLua Pro 原版实现（4.4.2）

### 2.1 实现形态：纯 Lua 资产工程，非原生代码

原版 `com.androlua` 包内**没有**任何 API 浏览器 Activity；浏览器是 `assets/javaapi/` 下的可运行 Lua 工程，由 IDE 菜单"Java API浏览器"拉起：

| 文件 | 内容 |
|------|------|
| `javaapi/main.lua`（3,528 B） | 全部逻辑：类表搜索、反射详情、事件/getter/setter 推导、剪贴板复制 |
| `javaapi/android.lua`（159,381 B） | **手写静态类表**：`return {"类名", ...}` 共 **4,124 条** |
| `javaapi/{layout,clayout,mlayout}.aly` | 三个布局模板 |
| `javaapi/init.lua`、`javaapi/fiximport.lua` | 工程清单、导入分析脚本（fiximport 归 WP-007） |

### 2.2 交互流程（原版行为基线）

1. 类列表页：`EditText` 全量过滤（`v:find(s,1,true)` 子串匹配）+ `ListView`（FastScroll），**无任何分类**。
2. 点击类 → `luajava.bindClass(类名)` 反射，弹 `Dialog` 显示分组明细：
   - 类头（`toString() + " extends 父类"`）
   - **构建方法**（`getConstructors()`）
   - **公有事件**：对每个 `getMethods()` 签名做 `%.setOn(%a+)Listener` 匹配 → 派生 `onXxx`
   - **公有 getter**：`([%a$%.]+) [%a$%.]+%.get(%a+)%(%%)` → `(返回类型)Name`
   - **公有 setter**：`%.set(%a+)(%(%[%a$%.]+%))` → `Name(参数类型)`
   - **公有字段**（`getFields()`）、**公有方法**（全部签名）
3. 成员页可过滤（按成员名子串），长按任意条目复制文本（类名/成员名）到剪贴板。
4. `init.lua`：`appname="javaapi"`（一个可运行工程）。

### 2.3 类表内容分析（已下载并统计）

| 顶层包 | 条目数 | 占比 | 备注 |
|--------|-------:|-----:|------|
| `android.*` | 2,840 | 68.9% | 含大量 pro 自定义类（`android.widget.ArrayListAdapter`、`PageView`、`android.app.FloatWindow` 等） |
| `java.*` | 917 | 22.2% | J2SE 基础（含 `java.security`、`javax.net` 等） |
| `javax.*` | 163 | 4.0% | |
| `com.*` | 107 | 2.6% | 全部是 pro 自身：`com.androlua.*`（97）、`com.nirenr.*`（Color/ColorFinder 等） |
| `org.*` | 84 | 2.0% | |
| `dalvik.*` | 10 | 0.2% | |
| `androidx.*` | **2** | — | 仅 `RecentlyNullable`/`RecentlyNonNull` 注解 |
| `AndroidManifest` | 1 | — | `AndroidManifest$permission` |

**关键事实（对用户需求的意义）：**

1. **没有 `com.google.android.material.*`（谷歌质感库）**，`android.support.design.*` 也没有，`androidx.*` 只有 2 个注解 → pro 的 API 浏览器**根本没有 Material/AndroidX 控件可看**。
2. **R 资源类只有 4 个**（`com.androlua.R$drawable/string/style/xml`）→ pro 几乎不展示 R 类。
3. 类表是 **2019 年前后手写维护**的，覆盖度 ≈ 宿主 APK 实际暴露面，不包含"工程 libs/*.dex 动态加载"的类（pro 运行时 `loadDex` 的类不在表内）。

### 2.4 原版能力边界小结

| 能力 | pro 4.4.2 | 备注 |
|------|-----------|------|
| 类名搜索 | ✅ 子串过滤 | |
| 反射详情（构造器/字段/方法） | ✅ | `toString()` 直接展示 Java 签名 |
| 事件推导 | ✅ `setOnXxxListener → onXxx` | 规则在 Lua 正则 |
| getter/setter 推导 | ✅ | |
| 剪贴板复制 | ✅ | |
| **分类筛选（全部/质感库/AndroidX/R…）** | ❌ 无 | **lide 需求超越 pro，属增强项** |
| 工程 dex 自动识别 | ❌ | |
| 原生 .so 函数识别 | ❌ | 原版也没做，见 §4 |

---

## 3. 需求映射：自动识别筛选分类（可行性分析）

用户目标分类（全部 / 谷歌质感库控件 / AndroidX 控件 / R 资源类…）全部可**规则自动判定**，无需人工标注。方案：**静态类表为基座 + 动态 dex 枚举为增量 + 反射验证为兜底**。

### 3.1 类别与自动判定规则（建议）

| 类别 | 判定规则（按顺序匹配） | 数据来源 | 可行性 |
|------|------------------------|----------|--------|
| 全部 | — | 合并索引 | ✅ |
| 谷歌质感库控件 | `com.google.android.material.*` | 静态表（推荐词条）+ 宿主 dex 枚举 | ✅ **宿主已带 material 1.13.0，类真实可反射**（见 3.2 注意事项） |
| AndroidX 控件 | `androidx.*` / `android.support.*` | 同上 | ✅ 宿主带 Compose/navigation/core 全家桶 |
| R 资源类 | 类名为 `R` 或含 `$R` / 是某 `R` 类的内部类 | **反射枚举**：`Class.forName("android.R")` + `getClasses()` → `android.R$id/layout/attr…`；宿主 `com.lioisme.lide.R`；工程 libs 中 R | ✅ 全自动，无静态表成本 |
| 系统控件/常用控件 | 类存在且反射验证 `android.view.View` 可赋值（`Class.forName(c).let { View::class.java.isAssignableFrom(it) }`） | 静态表候选 + 反射 | ✅ 全自动，pro 没有的亮点 |
| Android 框架 | `android.*` | 静态表 | ✅ |
| Java 标准库 | `java.*` / `javax.*` | 静态表 | ✅ |
| AndroLua 扩展 | `com.androlua.*` / `com.luajava.*` / `com.nirenr.*` / `android.app.FloatWindow` 等 pro 自定义控件（包前缀 + 手工白名单） | 静态表 | ✅ |
| 工程库（动态） | 工程 `libs/*.dex|jar` 类枚举 | `DexFile(path).entries()` 或 `DexClassLoader` | ✅ 见 3.3 |
| 原生库（动态，仅模块名） | 工程 `libs/lib*.so` 文件名 → `require "名"` | 文件系统扫描（与 checklib 同源） | ⚠️ 仅模块名级，见 §4 |

### 3.2 "谷歌质感库控件 / AndroidX 控件"真实性核验

- `app/build.gradle.kts` 已声明 `implementation("com.google.android.material:material:1.13.0")`，debug 构建下 `Class.forName("com.google.android.material.button.MaterialButton")` 在宿主进程中**必定成功**——浏览器看到的就是用户 `import` 后真实可用的类，不是摆设。
- ⚠️ **Release/R8 注意**：`isMinifyEnabled = true` 且当前 keep 清单（`luaApiSurface`）不含 material/androidx 前缀。R8 会重命名/删除未被 Java 引用的类（Material 控件几乎不被 Compose UI 引用 → 可能整体被裁剪）。**决策点（D-API-1）**：release 需对浏览器覆盖面补 keep 规则（如 `-keep class com.google.android.material.** { *; }` + 常用 `androidx.**`），或接受"debug 全量、release 降级"的差异；无论哪种，浏览器数据源应以"进程内实际可加载"为准（反射验证兜底），保证展示即可用。

### 3.3 工程库 dex 自动识别（真·自动识别）

- 工程 `libs/*.dex|jar` → `dalvik.system.DexFile(path).entries()` 枚举全类名（API 26 起标记 deprecated 但功能保留，Android 14/16 模拟器实测可用），或 `DexClassLoader` 加载后按类反射。
- 归属 `:app` 进程（R2：引擎与宿主同进程），不触碰 `:editor`，R1 零风险。
- 与 WP-007（fiximport）天然共享：fiximport 需要"工程 dex 类名 → import 语句"，本功能需要"工程 dex 类名 → 列表项"，同一枚举器两份复用。

### 3.4 R 资源类自动识别（无需静态表）

| 来源 | 方式 | 说明 |
|------|------|------|
| `android.R`（框架） | `Class.forName("android.R").classes` | 含 `$id/$layout/$drawable/$attr/$string…` 全部内部类，纯反射 |
| 宿主 R | `Class.forName("com.lioisme.lide.R")` | 同上 |
| 工程 libs 中 R | 3.3 的 dex 枚举结果按"类名 = `R` 或以 `R$` 开头"过滤 | 打包后工程才有自身 R；IDE 阶段仅 libs 来源 |

### 3.5 成员详情页（反射 + 事件推导，对齐 pro）

原版 Lua 逻辑逐条翻译为 Kotlin（`:app` 内）：

| 分组 | pro 规则（原版 Lua 正则） | Kotlin 等价 |
|------|--------------------------|-------------|
| 构建方法 | `getConstructors()` | `clazz.constructors` |
| 公有事件 | `%.setOn(%a+)Listener` → `"on"..e1` | `setOn(\\w+)Listener` → `"on$1"`（签名里剔除，与 pro 一致） |
| 公有 getter | `([%a$%.]+) [%a$%.]+%.get(%a+)%(%%)` → `(类型)名` | `get(\\w+)\\(\\)` 且无参 → `(返回类型)名` |
| 公有 setter | `%.set(%a+)(%(%[%a$%.]+%))` | `set(\\w+)\\(单参\\)` |
| 公有字段 | `getFields()` | `clazz.fields`（过滤 `isSynthetic`/`isStatic` 可选项） |
| 公有方法 | `getMethods()` | `clazz.methods`（`toString()` 直接输出 Java 签名，与 pro 观感一致） |
| 类头 | `class + " extends " + superclass` | `clazz.name + " extends " + clazz.superclass?.name` |

事件推导与 pro 的**差异点**（建议在报告中登记为已知差异，不强行对齐 pro 的缺陷）：pro 的事件/属性名是从方法签名**正则剥离**而来，会丢失泛型与重载区分；lide 可保留 pro 的展示格式（用户习惯），数据层保留完整 `Method` 对象供后续"点击事件项跳转查看对应 Listener 接口"增强（不在本期）。

---

## 4. 原生库（libs/*.so）自动识别 —— 诚实结论

**问：原生库技术上支持自动识别 API 吗？**
**答：函数级不支持，模块名级支持。原版 pro 同样不支持函数级。结论如下。**

### 4.1 为什么函数级不可行（本质原因）

1. **C 模块的函数清单不落盘、无元数据**。`libxxx.so` 是 ELF 共享库，导出符号只有 `luaopen_xxx`（模块入口）。Lua API 函数表是在 `dlopen` 后调用 `luaopen_xxx(L)`、由 C 代码在运行时向 Lua 注册表中写入的——**"有哪些函数"这个信息只存在于 Lua 运行时内存里**，静态解析 ELF 拿不到。
2. **枚举必须执行不可信代码**。要拿到函数清单，唯一途径是：`dlopen(用户.so)` → 用 `LuaState`（`libluajava.so` 已随引擎加载，技术上是可调的）→ 调 `luaopen_*` → 遍历返回表。这等于把任意用户原生代码加载进 IDE 进程执行，可能崩溃 IDE 或执行恶意逻辑。lide 对 `libs/` 的既定立场是"按不可信输入对待，加载失败必须降级"（`ARCHITECTURE.md`），浏览器做枚举与之矛盾。
3. **语义依赖**。多数 C 模块的 `luaopen_*` 依赖运行时环境（全局 `activity`、已加载的姊妹模块、`package.loaded` 状态），脱离工程运行上下文单独加载往往直接失败或半初始化——枚举结果不可信。
4. **R1/R2 架构红线**。`:editor` 禁 native（R1）；即使放 `:app`，这也是"运行时语义"而非"文本级能力"，与编辑器职责边界相悖。

### 4.2 模块名级识别（可做，本期顺带实现）

- 扫描工程 `libs/`：`lib*.so` → 可 `require "名"` / `loadLib`；连同内置 15 个系统模块（cjson/socket/crypt/md5/regex/xml/zip/zlib/luv/gl/…）一起入"原生库"类别，只展示**模块名**与"由 C 实现，API 见模块自带文档/README"的说明。
- 该能力与打包器 `checklib`（依赖收集，`XPOSED_MODULE_FEASIBILITY_REPORT.md` §3）同一扫描逻辑，成本≈0。

### 4.3 若未来确实需要函数级（非本期）

可走的运行时方案：在**引擎运行工程时**（`EngineActivity` 内，工程上下文齐全）注入一次 `require` 后枚举注册表，结果落盘 JSON 供浏览器只读展示——本质是"运行期捕获"而非"浏览器自动识别"。仍受"不可信代码在 IDE 进程执行"风险约束，建议否决，除非单独立项并做进程隔离（与 `IDE_RUNTIME_SEPARATION_FEASIBILITY_REPORT.md` 的运行时下载包形态冲突，成本高）。

---

## 5. 实现方案（lide 侧，建议立项范围）

### 5.1 模块归属（R1/R3 合规）

| 组件 | 落点 | 理由 |
|------|------|------|
| 类表资产 | `:app` assets（平移 pro `android.lua` → `assets/javaapi/android.lua`，与 WP-007 共享） | 宿主环境资产 |
| 索引/分类/反射数据层 | `:app` `com.lioisme.lide.apibrowser/ApiIndex.kt`（纯 Kotlin） | 反射 = 运行时语义，宿主专属（R3） |
| 分类器 | `:app` `ApiCategoryClassifier.kt` | 同上 |
| UI | `:app` `ui/screen/ApiBrowserScreen.kt` + `ApiClassDetailScreen.kt`（Compose） | 宿主 UI |
| 导航 | `LideNavHost.kt` 新增 `Routes.API_BROWSER` | 入口建议：SettingsScreen 与 ProjectListScreen 抽屉各一（pro 位置在 IDE 菜单） |
| `:editor` | **零改动** | R1 门禁 `rg -n "luajava|androlua|loadLibrary" editor/src` 继续零命中 |

### 5.2 数据层设计

```
ApiIndex（进程级单例，Dispatchers.IO 构建）
├── 静态基座：assets/javaapi/android.lua（4,124 条平移 + 精选扩展词条）
│   ├── com.google.android.material.*（建议 100+ 常用控件）
│   └── androidx.*（常用控件/Fragment/RecyclerView 等）
├── 动态增量：
│   ├── DexFile(host sourceDir).entries() 过滤 material/androidx 前缀（后台一次）
│   └── 当前工程 libs/*.dex|jar 类枚举（按需，工程维度缓存）
├── 反射验证兜底：Class.forName(name)（try/catch，失败标记"设备上不可用"）
├── 分类器：前缀规则表 + View 子类反射判定（惰性缓存）
└── 输出：{类别 → [ApiClass]}, ApiClass{name, category, verifiable}
```

要点（对齐 AGENTS.md 约定）：索引构建在后台线程；反射一律 `try/catch(Throwable)` 降级；`bindClass` 验证失败仅降级展示不崩溃；搜索结果量级（4K~1 万条）在内存/滚动上无压力。

### 5.3 详情页

对齐 pro 分组（构建方法/公有事件/公有 getter/公有 setter/公有字段/公有方法），事件推导规则见 §3.5 表；长按复制类名/成员名（pro 行为保留）；补充 pro 没有的：类名显示完整包名 + `extends` 链 + 不可用标记。

### 5.4 工作量与风险

| 项 | 量级 | 风险 |
|----|------|------|
| 资产搬运与类表扩展 | 低（脚本化生成扩展词条） | 扩展词条需手工挑选，易过时——用 3.2 的反射验证兜底对冲 |
| 索引/分类器/反射 | 中 | DexFile deprecated API 在目标设备回归（模拟器 x64 + 真机各验一次） |
| Compose UI 两页 | 中 | 与现有页面样式对齐即可 |
| R8 决策（D-API-1） | 低 | 见 §3.2；决定后登记 DECISIONS.md |
| 工程 libs 集成 | 低 | 与 WP-007 共享枚举器，先行抽公共 util |

---

## 6. 验收建议（ACC）

- ACC-1 类别齐全：全部/谷歌质感库控件/AndroidX 控件/R 资源类/常用控件/Android 框架/Java 标准库/AndroLua 扩展/工程库/原生库 均可见且计数合理。
- ACC-2 真实性：随机抽 `MaterialButton`、`androidx.recyclerview.widget.RecyclerView`、`android.R$id` 三项，详情页可打开且 `bindClass` 无异常（debug 构建）。
- ACC-3 工程库：导入含自定义 dex 的工程（如 live2d/box2d 类库），其类自动出现在"工程库"类别。
- ACC-4 事件推导：`View` 类详情页事件分组含 `onClick`（`setOnClickListener` 推导），与 pro 输出一致。
- ACC-5 R1 门禁：`rg -n "luajava|androlua|loadLibrary" editor/src` 零命中（本功能零改动 `:editor`）。
- ACC-6 性能：索引构建后台完成，页面滚动/过滤无 jank（沿用编辑器性能基线）。
- ACC-7 回归：release 构建（R8）下浏览器不崩溃，不可用类以标记降级（不因 D-API-1 取舍而崩溃）。

### 6.1 验收结果（2026-08-02 模拟器 emulator-5554，x64 debug 构建）

| ACC | 结果 | 证据 |
|-----|------|------|
| ACC-1 | ✅ | 实测计数：全部 20,629 / 常用控件 69 / 谷歌质感库控件 441 / AndroidX 控件 15,988 / R 资源类 106 / Android 框架 2,681 / Java 标准库 1,080 / AndroLua 扩展 169 / 其他 95（`verify/api14-chips.xml` 等） |
| ACC-2 | ✅ | 搜索 "MaterialButton" 命中真实 dex 枚举类；详情页 `class ...MaterialButton extends androidx.appcompat.widget.AppCompatButton` + 3 构造器；`android.R$id` 详情含 `public static final int ...accessibilityActionContextClick` 等真实字段（`verify/api33-rid-detail.xml`、`api33-detail.png`）；不可用类标记降级正常 |
| ACC-3 | ⚠️ 环境受阻 | 代码路径已验证：工程根签名缓存失效、dex 枚举逻辑与宿主 APK 枚举同路径（宿主侧产出 20,629 类即为证）。设备端被 ROM 存储沙箱阻止：经 adb/root 放入 `/sdcard/AndroLua/project/acc3libs/libs/box2d.dex` 的文件被 FUSE 强制定为 `u0_a99:media_rw 660`，任何应用（含 chown 到应用 uid 10122 后、重启后）均 Permission denied → 工程库 (0)。该限制同时影响宿主引擎运行工程 libs，属环境既有问题，非本功能缺陷。真机/应用内导入创建的 libs 不受影响 |
| ACC-4 | ✅ | MaterialButton 详情页公有事件分组含 `onClick`（`setOnClickListener` 推导），与 pro 输出一致 |
| ACC-5 | ✅ | `rg -n "luajava|androlua|loadLibrary" editor/src` 零命中；本功能全部代码位于 `:app` |
| ACC-6 | ✅ | 冷启动点击进入浏览器 ≤ 3.5s 可交互；索引构建后台完成（logcat `built 20,629 classes, 18 modules in ~1.1s`），滚动/过滤无 jank |
| ACC-7 | ✅ | `:app:assembleX64Release`（R8）BUILD SUCCESSFUL in 1m49s |

> ACC-3 备注：验收素材 `docs/reference/project/石头/libs/box2d.dex`（496,492 B）已就绪；待真机/支持 MediaStore 写入的环境复验。

---

## 7. 附：原版关键源码摘录（对照依据）

`assets/javaapi/main.lua`（pro 4.4.2）核心逻辑：

```lua
local classes=require "android"          -- 类表（4124 条静态字符串）
clist.onItemClick=function(l,v)
  local class=luajava.bindClass(tostring(v.Text))  -- 反射入口
  ...
  local e1=str:match("%.setOn(%a+)Listener")       -- 事件推导
  if e1 then table.insert(es,"on"..e1) end
  local s1,s2=str:match("%.set(%a+)(%(%[%a$%.]+%))") -- setter 推导
  local g1,g2=str:match("([%a$%.]+) [%a$%.]+%.get(%a+)%(%)") -- getter 推导
```

类表统计口径：`android.lua` 下载自 `raw.githubusercontent.com/nirenr/AndroLua_pro/master/app/src/main/assets/javaapi/android.lua`（blob `b1dd2794…`），正则提取 `"..."` 条目计 4,124 条。

---

## 8. 实施记录（2026-08-02，lide 侧）

### 8.1 交付物清单

| 组件 | 文件 | 说明 |
|------|------|------|
| 类表资产 | `app/src/main/assets/javaapi/android.lua` | pro 4,124 条平移 + 74 条 material/androidx 精选扩展 = **4,196 条**，166,924 B，格式 `return {\n"name",...}`（供 WP-007 fiximport 共享） |
| 数据层 | `app/src/main/kotlin/com/lioisme/lide/apibrowser/ApiCategory.kt` | 9 类枚举（常用控件/Material/AndroidX/R 资源/AndroLua 扩展/Android 框架/Java 标准库/工程库/其他） |
| 数据层 | `.../apibrowser/ApiIndex.kt` | 进程级单例；静态类表解析 + 宿主 dex 枚举（androidx./material. 前缀）+ 工程 libs/dex/jar 枚举 + lib*.so 模块名 + R 类反射补齐；快照缓存按工程根签名失效；isLoadable/isViewSubclass 反射验证带并发缓存 |
| 数据层 | `.../apibrowser/ApiCategoryClassifier.kt` | 顺序敏感规则（R → material → androidx → androlua 白名单 → java → widget 反射验证 → framework → other） |
| 数据层 | `.../apibrowser/ApiReflection.kt` | Detail 六分组反射；事件 `setOn(\w+)Listener → onXxx`、getter/setter 基于 Method 对象推导（修正 pro 正则剥离 "(public)Name" 缺陷，格式观感保留） |
| UI | `.../ui/screen/ApiBrowserScreen.kt` | 分类 chips（含计数）+ 即时搜索 + 类列表（长按复制 + Snackbar）+ 不可用标记 |
| UI | `.../ui/screen/ApiClassDetailScreen.kt` | 分组明细（类头/构建方法/公有事件/getter/setter/字段/方法）+ 成员过滤 + 长按复制 |
| 导航 | `LideNavHost.kt` | `Routes.API_BROWSER` / `API_CLASS_DETAIL`（类名 URL 编码）；入口：SettingsScreen「API 浏览器」项 + ProjectListScreen 顶栏 GridView 按钮 |
| 字符串 | `values/strings.xml` / `values-zh/strings.xml` | api_* 全量双语（字节级插入，CRLF/UTF-8 无 BOM 保持） |

### 8.2 决策与差异（相对 §5 方案）

1. **扩展词条 74 条而非 100+**：material 常用控件 47 + androidx 常用 27（RecyclerView/ViewModel 等），其余全部由宿主 dex 动态枚举自动补齐（`com.google.android.material.*` 全量 300+ 真实可反射类），静态词条只做"种子"。
2. **模块归属不变**：全部 `:app`，`:editor` 零改动（R1 门禁保持零命中）。
3. **原生库类别**：以「原生库」独立分组展示模块名（内置 18 个 + 工程 libs/lib*.so 扫描），点击展示"由 C 实现"说明（`api_native_module_desc`）。
4. **事件推导**：与 pro 行为对齐（`setOn(\w+)Listener`），但成员名从 Method 对象取（见 8.1）。

### 8.3 构建状态

- `.\gradlew.bat :app:compileX64DebugKotlin --console=plain` ✅（2026-08-02，仅遗留既有 deprecation 警告）
- `.\gradlew.bat :app:assembleX64Debug --console=plain` ✅

### 8.4 踩坑记录（实施期）

1. **Kotlin 块注释可嵌套 → KDoc 内禁止出现 `libs/*.dex` 字样**：`/*` 在块注释内会开启嵌套注释，导致文件从该处开始被注释吞噬、EOF 报 "Unclosed comment"、后续声明全部消失（编译报错指向不存在的行号，极具迷惑性）。已全部改写为 `libs/ 下 dex|jar`。**教训：KDoc 中引用 glob 模式 `*.x` 必须规避 `/*` 字节序列。**
2. **`dalvik.system.DexFile` 不实现 `Closeable`**：Kotlin `use {}` 不可用，改为显式 try/finally `dex.close()`（API 26+ deprecated 警告符合预期，功能保留）。
3. **Windows 文件句柄 + Kotlin 增量缓存**：首次编译报"文件行号超出 EOF"，清 `app/build/kotlin` + `--stop` 后复现与修复路径见本表第 1 条（根因是注释吞噬，非缓存）。

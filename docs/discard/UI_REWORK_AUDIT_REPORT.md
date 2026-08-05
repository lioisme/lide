# lide UI 重构 —— 审计违规清单与解决方案（Audit & Work Order）

| Metadata | Value |
|----------|-------|
| Document ID | LIDE-UI-002 |
| Status | PASS（2026-08-02 设备验证完成） |
| Author | Audit Team |
| Priority | P0 —— 5 项 UI 需求均为产品规范硬性要求 |
| 验证方式 | x64 安卓模拟器（emulator-5554）ADB 定向 / 回归 / 全量 |
| 关联 | AGENTS.md、docs/discard/FULL_AUDIT_REPORT_2026-08-01.md |

---

## 一、违规清单（基于设备实测基线，2026-08-02）

模拟器：emulator-5554（x86_64，1440×2560 @ 640dpi），实测数据来自
`adb shell uiautomator dump`（屏幕坐标，px）。当前安装版本与工作区源码一致
（git f5a3c9d）。

### 违规 1（P0）：工程列表 item 上存在导出按钮；长按菜单非底部对话框

**现状（设备实测）**：
- `app/src/main/kotlin/com/lioisme/lide/ui/screen/ProjectListScreen.kt:240-249`
  每个 item 右侧常驻「导出为 .alp」IconButton（uiautomator 可见 6 个
  `content-desc="导出为 .alp"`，x=1232..1328）。
- 长按触发的是 `DropdownMenu`（ProjectListScreen.kt:251-277），非底部对话框。

**违反需求 1**：长按 item 必须调出底部对话框，功能全部收进底部对话框，
item 上的导出按钮移除。

**解决方案**：
1. 删除 item 内导出 IconButton（Row 末尾仅保留名称/包名）。
2. 长按改用 `ModalBottomSheet`（M3 1.3.0 可用），sheet 内放三个功能项：
   工程信息 / 导出为 .alp / 删除（每项带图标，复用现有回调与
   `pendingExportProject` / `pendingDeleteProject` 状态）。
3. 删除原 `DropdownMenu` 分支。

### 违规 2（P0）：编辑器 Appbar 共 7 个按钮，超出 5 个上限

**现状（设备实测，按钮 x 坐标）**：返回(64) / 打开文件树(256) / 撤销(512) /
重做(704) / 搜索(896) / 运行(1088) / 更多(1280)，共 7 个。

**违反需求 2**：Appbar（含返回）最多 5 个按钮：返回、运行、撤销、重做、更多。

**解决方案**（`editor/src/main/kotlin/com/lioisme/lide/editor/EditorToolbar.kt`）：
1. actions 区仅保留：运行、撤销、重做、更多（按需求顺序）。
2. 「打开文件树」「搜索」移入更多菜单。
3. 更多菜单按分类展示（DropdownMenuHeader）：
   - 文件：打开文件树、搜索
   - 工程：工程信息、工程权限、构建 APK（宿主注入）
4. `EditorMoreAction` 增加 `category: String?` 字段，宿主按分类注入；工具栏按
   category 变化插入分类标题。

### 违规 3（P0）：Tab 标签与标签栏之间有距离，浪费编辑器空间

**现状（设备实测）**：`EditorTabs.kt:89` LazyRow `contentPadding =
PaddingValues(horizontal = 8.dp, vertical = 4.dp)` —— 上下各 4dp 内边距使标签
悬浮在条内，标签与标签栏存在可见间隙。

**违反需求 3**：标签与标签栏之间无距离。

**解决方案**：
1. `contentPadding` 移除 vertical 分量（仅保留 horizontal 8dp）。
2. 为标签行加 `surfaceContainer` 背景，使标签条成为一体式条带（视觉上标签
   与标签栏贴合），无悬浮间隙。

### 违规 4（P0）：lide 界面按钮普遍无 tip（tooltip）提示

**现状**：全 App 图标按钮仅提供 `contentDescription`（无障碍语义），
长按无 tooltip 浮层。涉及：
- 编辑器工具栏：返回/文件树/撤销/重做/搜索/运行/更多（EditorToolbar.kt）
- 标签关闭按钮（EditorTabs.kt）、搜索面板 上一条/下一条/关闭（SearchBar.kt）
- 工程列表顶栏：示例/导入 .alp/新建/设置（ProjectListScreen.kt）
- 文件目录对话框：上一级/关闭（EditorScreen.kt:514,551）
- 各子页返回键：设置/示例/插件/权限/工程信息/构建 APK/Lua 手册/宿主权限
- 插件页安装 .alp 按钮（PluginScreen.kt:116）

**违反需求 4**：按钮需要有 tip 显示。

**解决方案**：
1. `:editor` 新增通用组件 `TipIconButton`（M3 1.3.0 `TooltipBox` +
   `PlainTooltip` + `rememberTooltipState`，长按显示，文本入参，无资源依赖，
   供 `:app` 复用，满足 R4 边界）。
2. 逐按钮替换 IconButton → TipIconButton，tooltip 文本复用/新增 `cd_*`
   字符串（中英双语）。
3. 文本按钮（TextButton/FilledTonalButton/FilterChip）不强制加 tip。

### 违规 5（P0）：菜单按钮的菜单移位，未显示在应处位置

**现状（设备实测）**：
- 编辑器更多（⋮）菜单：按钮在 x=1280..1376，菜单却出现在 x=512..750
  （与 actions Row 起点对齐），水平偏移约 768px —— 严重移位。
- 工程列表长按菜单：菜单 x=48、y 与 item 底部存在额外间隙。
- Tab 菜单：x=80 对齐 tab 文本起点，y=600 与 tab 底部有明显间隙。

**根因（源码级确认）**：compose-ui 1.7.1 `Popup` 锚点 = DropdownMenu
调用处的**直接父布局节点**（`Popup$7$1.invoke` 取 `it.parentLayoutCoordinates`
计算 `anchorBounds`，再交 `DropdownMenuPositionProvider.calculatePosition`）。
- 工具栏菜单直接写在 TopAppBar `actions` Row 内 → 锚点=整行
  （x 起点 512）→ 菜单整体左移。
- 标签菜单直接写在 tab Box 内 → 锚点=外层容器 → 偏移。
- 工程列表菜单同理。

**解决方案**：所有菜单一律「按钮 + DropdownMenu」用 `Box` 包裹，使锚点 =
紧贴按钮的 Box（x/y 与按钮一致），菜单即显示在按钮正下方（需求 5）。
涉及：EditorToolbar 更多菜单、EditorTabs 标签菜单；工程列表长按改底部
对话框后天然无此问题。

---

## 二、架构合规

- 所有改动集中在 `:editor`（工具栏/标签/搜索/通用组件）与 `:app`
  （宿主界面），不触碰 `:androlua` 与 `libluajava.so`。
- `TipIconButton` 放 `:editor`，`:app` 复用（宿主→编辑器单向依赖）。
- 验证：`rg -n "luajava|androlua|loadLibrary" editor/src` 零命中。

## 三、验证计划（ADB，emulator-5554）

| 级别 | 用例 | 断言 |
|------|------|------|
| 定向 | 长按工程 item → 底部对话框出现（工程信息/导出/删除三入口） | uiautomator 文本命中 |
| 定向 | item 上无「导出为 .alp」按钮 | content-desc 计数=0 |
| 定向 | 编辑器 Appbar 按钮数=5（返回/运行/撤销/重做/更多），无文件树/搜索 | content-desc 集合精确匹配 |
| 定向 | 更多菜单：分类标题（文件/工程）+ 5 项功能；菜单 x 与 ⋮ 按钮对齐 | 菜单 bounds.x ≥ 按钮.x |
| 定向 | Tab 栏：标签与栏无 4dp 间隙（栏高=标签高） | bounds 计算 |
| 定向 | 长按各图标按钮出现 tooltip 浮层 | uiautomator 文本命中 |
| 回归 | 4 基准工程（demo/DemoProject/kleinbottle/testjson）构建+安装+运行 | adb_regress.ps1 矩阵 |
| 回归 | `:app` / `:editor` / `:androlua` 单元测试全量 | gradlew test |
| 全量 | assembleX64Debug + 安装 + 主流程走查（列表→编辑器→设置→权限→构建页） | 无 crash（logcat） |

## 四、文件改动清单

| 文件 | 变更 |
|------|------|
| editor/.../EditorToolbar.kt | 5 按钮 + 分类菜单 + Box 锚定 + TipIconButton |
| editor/.../EditorTabs.kt | 去垂直内边距 + 背景 + 关闭按钮 tip |
| editor/.../SearchBar.kt | 3 个图标按钮加 tip |
| editor/.../Tooltip.kt（新增） | TipIconButton 通用组件 |
| editor/.../res/values*/strings.xml | 补充 cd_* 提示文案 |
| app/.../ProjectListScreen.kt | 移除 item 导出按钮 + 长按底部对话框 + 顶栏 tip |
| app/.../EditorScreen.kt | 文件浏览器按钮 tip |
| app/.../SettingsScreen.kt 等 7 个页面 | 返回键 tip |
| app/.../PluginScreen.kt | 安装按钮 tip |
| app/.../res/values*/strings.xml | 分类名 + 提示文案 |

## 五、设备验证结果（2026-08-02，emulator-5554）

| 用例 | 断言 | 结果 |
|------|------|------|
| 长按工程 item → 底部对话框 | 「demo / Project Info / 导出为 .alp / 删除」+ 拖动手柄 | PASS |
| item 无导出按钮 | 列表页 content-desc 无「导出为 .alp」按钮（基线 6 个 → 0） | PASS |
| 编辑器 Appbar = 5 按钮 | 返回[64,176] 运行[704,176] 撤销[896,176] 重做[1088,176] 更多[1280,176] | PASS |
| 更多菜单锚定 | 菜单 x=1024..1360 贴 ⋮ 按钮（基线 x=512 左移）；分类标题「文件/工程」+ 5 项 | PASS |
| Tab 栏零间距 | 关闭按钮高 56px ≈ tab 高 57px，栏背景 y=352 起连续同色无 4dp 缝隙 | PASS |
| Tooltip | 长按「返回」出现浮层「返回」@ [61,112][162,182] | PASS |
| 更多菜单功能 | 打开文件树（demo/init.lua/main.lua 可见）、搜索栏（关闭搜索）均正常 | PASS |
| 回归 4 工程 | demo（EngineActivity 前台）/ DemoProject（page\|calc）/ kleinbottle（GL）/ TestJson（finalize） | PASS |
| 单元测试 | :editor:testDebugUnitTest + :app:testArm64DebugUnitTest | PASS |
| 架构合规 | editor/src 无 luajava/androlua/loadLibrary，editor 不依赖 :androlua | PASS |

注：adb_regress.ps1 中工程名 `testjson`（小写）与设备实际目录 `TestJson` 不符，
样例推送段（assets/samples）因 c04f73a 改版失效，回归矩阵为手动执行等价断言。

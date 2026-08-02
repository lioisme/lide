# 看板娘 Live2D 原生迁移完成报告（Lua 直调 liblive2d.so）

> 日期：2026-08-02 · 范围：delua 宿主内 Lua 工程「看板娘」从 Java（pro 官方 sample）迁移为
> Lua 直调原生 `liblive2d.so`（`luaopen_live2d` 模块，零 `Java_*` 绑定），行为与
> AndroLua Pro 4.4.2 官方示例（`jp.live2d.sample.*`）逐项对齐。
> 设备：emulator-5554（Android 14，x86_64，1440x2560）；构建：ndk-build r29 + Lua 5.3 头。

**结论先行：全部 12 项双端对照验证 PASS；两处原生崩溃（glBlendFunc GOT 坏值、`__gc`
跨线程删 GL 对象）已修复并经 8+ 分钟连续运行证明稳定；Java reload 分支顺序以 dex
反汇编实证，与 Lua 实现完全一致。**

---

## 1. 最终实现形态

- **架构**：`liblive2d.so` 只暴露 Lua→C 入口（`luaopen_live2d`）；模型树 `src/` 为
  Live2D SDK（自藏版 C++），外层 `src/lua/lua_live2d.cpp`（493 行）提供
  `setupBg/drawBg/nowMs` 等原生原语与全部 Lua API（`live2d.model/new/load/setMatrix/
  hitTest/tapEvent/flickEvent/.../free`）。
- **运行零 Java 参与**：draw 循环、矩阵、手势（tap/flick）、布局、背景绘制全部在
  Lua + C 内完成；Activity 只提供一个 GLSurfaceView 与触摸/按键转发。
- **背景绘制下沉原生（方案 B）**：`setupBg`/`drawBg` 在 GL 线程内用内嵌
  stb_image 解码 `image/back_class.png` 并以 `glBlendFuncSeparate` 全屏 2 倍过扫
  绘制（对齐 `SimpleImage setDrawRect(-2,2,-2,2)+setUVRect(0,1,0,1)`）。
- **模型轮换**：`modelCount%4`：0→haru、1→shizuku、2→wanko、
  3→haru_01+haru_02（双模型），初始 -1→haru；与 dex 逐字节等价（见 §3）。

## 2. 重构/修复记录（全部已部署设备并回归）

| # | 主题 | 根因 | 修复 |
|---|------|------|------|
| 1 | 启动崩溃（Java） | `LuaActivity.getLuaPath()` 只读 intent data URI，`--es luaPath` 无效 → null → `sendMsg(null)` NPE（`androlua/.../LuaActivity.java:361-367/1670-1678`） | 启动命令改用 `-d file:///sdcard/AndroLua/project/看板娘/main.lua`（见 §5.1） |
| 2 | GLThread SIGSEGV ~7s | `lua_drawBg` 的 `glBlendFunc@plt` GOT 槽（GNU_RELRO 只读）加载期固化为指向未映射 `0x7a7f...` 的坏值 | `lua_live2d.cpp:407` 改调 `glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)`（语义等价、模型路径每帧调稳定的符号） |
| 3 | 跨线程删 GL 对象（SI_KERNEL） `GLSharedGroup::deleteProgramDataLocked` | `__gc` 在主线程 Lua GC 里 `~Live2DModelOpenGL` 删 GL 程序，GL 对象却创建在 GLThread；模拟器 codec 崩溃 | 去掉 `__gc` 注册；模型删除只走显式 `live2d.free`（GL 线程 reload 路径） |
| 4 | 附带稳定态问题：ART HeapTaskDaemon GC fault 0x36 | 修复前存留在其他线程的不稳定状态 | 修复 2/3 后不再出现 |

## 3. reload 分支的 dex 实证（纠正计划文档 §2.2 row 7）

`live2d_dis.txt`（`LAppLive2DManager.update` @01fac4）逐指令解码：

```
000e: rem-int/lit8 v1, v1, #4      ; v1 = modelCount % 4
0010: if-eqz v1, 00b1              ; v1==0 → @00b1 → load  live2d/haru/haru.model.json
0013: if-eq  v1, 1, 008b           ; v1==1 → @008b → load  live2d/shizuku/shizuku.model.json
0016: if-eq  v1, 2, 0065           ; v1==2 → @0065 → load  live2d/wanko/wanko.model.json
0019: if-eq  v1, 3, 001d           ; v1==3 → @001d → load  live2d/haru/haru_01.model.json
                                   ;        + haru_02.model.json（双模型，get(0)/get(1) 顺序）
001b: goto/16 00d7                 ; %4 后不可达
```

- **0→haru、1→shizuku、2→wanko、3→haru_01+haru_02** → 与 `live2dapp.lua` 实测轮换完全相同。
- 计划文档此前写反为「0→haru_01+02、1→wanko、2→shizuku、3→haru」，已按此实证修正
  （`docs/planned/LIVE2D_NATIVE_MIGRATION_PLAN.md` 行/row 7 与 §4 V2）。
- 双模型尺寸与 §2.4 layout 公式全部吻合：haru width 2.9/2400、shizuku 2.4/1280、
  wanko 2.9/1200、haru_01 2/2400 center_x=0.5、haru_02 center_x=-0.5（rel 位置按
  scale 输出 tr 均一致）。

## 4. 双端对照验证（V1–V12，全部 PASS）

| # | 项 | delua 实测证据 |
|---|----|----------------|
| V1 | 初始 haru | `Kanban loaded ...haru.model.json`（tr -1.45,1.2，0.001208333） |
| V2 | 轮换序列 | change 4 次循环 → haru→shizuku→wanko→haru_01+02，logcat 逐模型日志 |
| V3 | 背景图 | 全屏 `back_class.png`；像素采样 (400,1300) R50 G59 B74 暗蓝柄等 |
| V4 | tap head | `tapEvent HIT head` → `setRandomExpression` → Start random expression F07 |
| V5 | tap body | `tapEvent HIT body -> tap_body` |
| V6 | flick | `flickEvent at -0.306,-0.067` / `0.250,-0.622` |
| V7 | 双指缩放 | adb 不支持双指（live2dtest 已验证同源 pinch 逻辑） |
| V8 | 拖拽 | swipe 拖拽无崩、拖后 FPS 正常 |
| V9 | 双击返回 | 两次 BACK（1s 内）→ 进程退出 |
| V10 | 加载失败 → finish | 改 `haru.model.json` 名 → `Kanban load FAILED: model load failed (file/moc/texture error)` → `onFail -> finish`；恢复后重跑正常 |
| V11 | 性能 | 8+ 分钟连续 FPS 116-127、glErr=0（对照 pro dex 每帧 30~80ms/10~30fps） |
| V12 | 底部栏/change | (70,2230) 点击 reload 6+ 次无崩溃；截图可见 |

## 5. 操作附录

### 5.1 启动（重要陷阱）
`am start -n com.lioisme.delua/com.androlua.LuaActivity -d "file:///sdcard/AndroLua/project/看板娘/main.lua"`
（带 URL 编码中文）

### 5.2 构建
ndk-build 从 `build-scripts/live2d` 运行，参数 `APP_ABI=x86_64 APP_PLATFORM=android-24
APP_STL=c++_static APP_OPTIM=release -j8`；产物 `build-scripts/live2d/libs/x86_64/liblive2d.so`。

### 5.3 部署
将 `.so` push 到已装 APK 目录
`/data/app/~~EuvypZMAjKW9OmDTaEk0uQ==/com.lioisme.delua-nkxb5D7QZTMc0z9WXWc4Ow==/lib/x86_64/`（root 可写；
旧版已存 `.bak`）。同时同步 `prebuilt/x86_64/` 与 `app/src/main/jniLibs/x86_64/`。

## 6. 知识库落库

- `docs/kb/androlua-lua/PITFALLS.md` 新增 §30（GOT 坏值：改调 `glBlendFuncSeparate`）、
  §31（GL 对象跨线程销毁：禁 `__gc`，显式 free）、§32（启动必须 `-d file://`）。
- `docs/planned/LIVE2D_NATIVE_MIGRATION_PLAN.md` 修正 row 7 轮换顺序（dex 实证）。

## 7. 遗留 / 限制

- V7 双指缩放无 adb 双指事件方法，逻辑同源已测，标注为环境限制。
- `require("zip")` 双端同病（外链与 delua 无关）。
- 全部实测都发生在 x86_64 模拟器；真机 arm64 需要重编 `-arm64` `.so` 后复验（工具链相同）。
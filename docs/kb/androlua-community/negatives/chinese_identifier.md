# 负面主题卡：中文标识符（命名不规范）

> reviewed: 2026-08-02 ｜ confidence: static_review ｜ 判罚依据：STANDARDS §4.1-1

## 1. 现象与危害

社区帖大量使用**中文函数名/变量名**（Lua 5.3 允许 UTF-8 标识符，可运行，但）：

- 无法在 Java 侧 / JS 桥 / 混淆工具中稳定引用；
- 与英文库代码混排后可读性反而下降（中英夹杂）；
- 中文标点与全角字符混入极易造成语法事故；
- 大模型训练与补全工具对中文标识符的建模噪声大。

## 2. 代表样本

```lua
-- id 76（实用代码）：
function 隐藏输入法(控件id)
  import "android.view.inputmethod.InputMethodManager"
  activity.getSystemService(Context.INPUT_METHOD_SERVICE)
    .hideSoftInputFromWindow(控件id.getWindowToken(),0)
end
-- 调用：隐藏输入法(填写编辑框控件ID)   ← 参数名还带中文说明
```

```lua
-- id 3833（弹窗背景模糊）：function 弹窗背景模糊() ... end
-- id 298（卡片布局）：function 卡片(a,b,c)  ffrr= {...} end
-- id 2319（MaterialButton）：local function 随机色() ... end
-- id 1490（文字图标）：全屏框架={...}          ← 变量名也是中文
-- id 1327（微云授权）：function 边框圆角(边宽度,边框色,...) ... end
```

## 3. 修正写法（SOP）

```lua
-- 改前：function 隐藏输入法(控件id) ... end
-- 改后：
local function hideInput(editText)
  local imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE)
  imm.hideSoftInputFromWindow(editText.getWindowToken(), 0)
end
```

1. 函数名用英文小驼峰（`hideInput`/`roundCorners`/`randomColor`）；
2. 参数名表达语义（`editText` 而非 `控件id`）；
3. 中文仅允许出现在注释与字符串中。

## 4. 社区声明对照

- id 2183《中文函数》作者自己声明：*"正式项目中，建议不要用中文名来定义函数"*——
  说明作者明知不规范仍上传（该帖另含注释里塞广告的问题）。
- 判定：`chinese_identifier` 命中即 `negative`（除非整帖本身就是"中文标识符演示"主题帖）。

## 5. 相关帖子清单（抽查）

76, 3833, 298, 2319, 1490, 1327, 2714, 2195, 4252, 1707, 1728, 4075, 3472, 1250,
4558, 4508, 4210, 4231, 1960, 1250 等（全量见 catalog/post_*.json flags）。

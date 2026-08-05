# AndroLua Pro 语法手册

> ⚠️ **可信度声明（重要）**：本手册全部内容均基于对 AndroLua Pro 4.4.2 源码的**静态审计（理论分析）**与官方 Lua 5.3 手册核对，
> **未经设备实测**，属"**未经测试，仅供参考**"内容。文中标注的源码引用位置（路径 + 行号）供复核用，
> 不构成行为保证；与真机/模拟器实测结论冲突时**一律以实测为准**（双端验证方法见 `TECHNIQUES.md` §3）。
>
> 依据：AndroLua Pro 4.4.2 源码（`reference/androlua/AndroLua_pro-master/`）+ 官方 Lua 5.3 参考手册（该工程自带 `app/src/main/assets/luadoc/manual.html`）。
> 本手册描述的是 **pro 原版** 的 Lua 工程侧语法与 API；lide 若出现差异以实测为准（见 `FEATURES.md` / `PITFALLS.md`）。
> 涉及源码的结论均标注引用位置（相对该工程的路径，行号取自本次审计）。

---

## 1. 版本与架构

| 项 | 值 | 依据 |
|---|---|---|
| Lua 版本 | **Lua 5.3.3**（integer/float 双数字子类型，含位运算、utf8 库） | `app/src/main/jni/lua/lua.h`: `LUA_VERSION_NUM 503` |
| 桥接库 | `libluajava.so`，Lua 侧库名 `luajava`，版本 `_VERSION = "4.0"`，作者标记 `by nirenr` | `jni/luajava/luajava.c` l.1430-1432 |
| 宿主包 | `com.androlua`（LuaActivity / LuaService / LuaApplication / LuaContext） | `app/src/main/java/com/androlua/` |
| 运行时脚本 | `app/src/main/resources/lua/`（25 个 .lua + socket/ 子目录） | 同上 |
| 运行入口 | Android 原生 Activity，Lua 侧入口为工程 `main.lua`（或 service.lua / init.lua 元数据） | `LuaActivity.java` l.245 |

三层结构：

```
┌──────────────────────────────────────────────┐
│  Lua 脚本层：main.lua / init.lua / 布局表 / 模块  │  ← 本手册主要对象
├──────────────────────────────────────────────┤
│  桥接层 libluajava.so + com.luajava.*          │  ← Java↔Lua 互操作语法（第 4 章）
├──────────────────────────────────────────────┤
│  宿主层 com.androlua.*（Activity/Service/线程/组件）│  ← 全局注入与 API（第 5 章）
└──────────────────────────────────────────────┘
```

运行机制要点：

- 宿主在创建 LuaState 后调用 `openLibs()`（= 标准库 + `luajava` 库 + 8 个基本类型全局名）再注入宿主对象（`LuaActivity.java` l.1442-1504）。
- `activity`（或 `service`）被压栈为全局 userdata（`pushJavaObject`），其公共方法/字段在 Lua 中直接可见。
- 脚本执行统一走 `load + debug.traceback 包裹 + pcall`（`LuaActivity.java` l.1568-1635），Lua 错误经 `sendError` 上报，可被全局 `onError(title, msg)` 拦截。
- 所有线程（LuaThread/LuaTimerTask/LuaAsyncTask）各自持有**独立 LuaState**，跨线程通信靠 `set`/`call` 全局函数经 Handler 转发。

---

## 2. Lua 语言基础（5.3 规范参考）

完整规范见官方手册（工程自带 `assets/luadoc/manual.html`）。本节是与 AndroLua 工程写法直接相关的要点。

### 2.1 词法约定

- **标识符**：`[A-Za-z_][A-Za-z0-9_]*`，大小写敏感。保留字共 22 个：

```
and      break    do       else     elseif   end
false    for      function goto     if       in
local    nil      not      or       repeat   return
then     true     until    while
```

- **注释**：
  - 单行：`-- 注释`
  - 长注释：`--[[ 注释 ]]`，`--[=[ 注释 ]=]`（可嵌套等号层）
- **数值字面量**（5.3 有整数/浮点子类型）：
  - 十进制：`3`、`3.0`、`3.1416`、`314.16e-2`、`0.6E1`
  - 十六进制：`0xff`、`0x1.8p1`（十六进制浮点，`p` 指数）、`0xFFFF`
  - 运算符要求两侧同为整数或同为浮点；`3/2` 恒为浮点 `1.5`，整除用 `//`，取余 `%`
- **字符串字面量**：
  - 短字符串 `"..."` / `'...'`；转义：`\a \b \f \n \r \t \v \\ \" \' \0`、`\xXX`（两位十六进制）、`\ddd`（至多三位十进制，≤255）、`\u{XXX}`（UTF-8 码点）
  - 长字符串 `[[...]]` / `[=[...]=]`：不处理转义，可跨行
  - 字符串是**字节串**，与编码无关（中文按 UTF-8 字节存）；`#s` 为字节长度
- **分号可选**；行尾 `;`、空行均可作语句分隔。

### 2.2 类型系统（8 种）

| 类型 | 说明 | 判断函数 |
|---|---|---|
| `nil` | 空值，`nil` 即"不存在" | `type(x)=="nil"` |
| `boolean` | `true`/`false`；只有二者是布尔，0 与空串**都是真** | `type(x)=="boolean"` |
| `number` | 整数（Lua 整数，64 位）与浮点两个子类型；`math.type` 区分 | `type(x)=="number"` |
| `string` | 字节串 | `type(x)=="string"` |
| `table` | 表，数组+字典一体 | `type(x)=="table"` |
| `function` | 函数（一等值） | `type(x)=="function"` |
| `userdata` | **Java 对象在此**（见 4 章） | `type(x)=="userdata"` |
| `thread` | 协程 | `type(x)=="thread"` |

- 表下标从 **1** 开始；`#t` 只对"数组部分"（1..n 连续非 nil）可靠。
- 字符串与数字参与算术时自动转数字（`"10"+1 == 11`）；`..` 拼接时数字转字符串（`"a"..1 == "a1"`）。

### 2.3 运算符与优先级（从高到低）

| 优先级 | 运算符 |
|---|---|
| 1 | `^`（右结合） |
| 2 | `-`（一元负）、`#`（长度）、`~`（一元按位非） |
| 3 | `*` `/` `//` `%` |
| 4 | `+` `-` |
| 5 | `<<` `>>` |
| 6 | `&`（按位与） |
| 7 | `~`（按位异或） |
| 8 | `\|`（按位或） |
| 9 | `<` `>` `<=` `>=` `~=` `==` |
| 10 | `and` |
| 11 | `or` |
| 12 | `..`（右结合） |

- `not` 是前缀运算符，优先级高于比较（`not a == b` 意为 `(not a) == b`）。
- `and`/`or` 短路且返回参与运算的**原值**（`a and b`：a 为假返 a，否则返 b）。
- `x = x or 默认值` 是工程里最常见的默认值写法（注意 `false`/`0` 也会被 or 替换掉，需要时用 `x == nil` 判断）。

### 2.4 语句

```lua
-- 赋值（可多重赋值，右侧多退少补 nil）
a, b = 1, 2
a, b = b, a            -- 交换
-- 局部变量
local x = 10
local a, b = 1, 2       -- 局部可多变量

-- 控制
if cond then ... elseif cond2 then ... else ... end
while cond do ... end
repeat ... until cond
for i = 1, 10 do ... end              -- 数字型（step 可负/浮点）
for k, v in pairs(t) do ... end       -- 通用型
for i, v in ipairs(t) do ... end      -- 数组型（1..n 连续）
for c in utf8.codes(s) do ... end
break / goto label / ::label::       -- goto 有限支持

-- 函数
function f(a, b, ...) local c = ... end
local f = function(a) return a end
f = function(a, ...) return a, ... end   -- 多返回值
-- 调用括号可省略（字符串/表字面量参数）：
print "hi"
OnClickListener{ onClick = fn }       -- 等价 OnClickListener({onClick=fn})，见 4 章

-- 返回
return a, b    -- return 必须是一个块的最后语句（通常前加空行或 do end 包裹）
```

- 变量默认是全局（挂在 `_G`）；未声明的全局名读取为 `nil`。
  **AndroLua 特例**：全局 metatable 的 `__index` 会把未知全局名尝试解析为 Java 类（见 4.3）。
- 多重返回规则：`return f()` 只传 f 的全部返回值；`(f())` 括号会截断成单值；`local a = f()` 取第一个。

### 2.5 闭包与 Upvalue

```lua
function counter()
    local n = 0
    return function() n = n + 1 return n end   -- 闭包捕获 n
end
```

事件回调、适配器、线程里大量使用闭包（见第 6、7 章）。

### 2.6 表与元表

```lua
t = {}; t.x = 1; t[1] = "a"            -- 构造
t = { "a", "b", key = 1 }              -- 混合：数组部分+字典
t = { [1]="a", ["k"]=1 }

-- 元表（metatable）控制表的运算符与索引行为
mt = { __index = function(t, k) return 默认 end }
setmetatable(t, mt)
```

5.3 元方法全集：

| 元方法 | 触发 |
|---|---|
| `__index`（表或函数） | 读 `t.k` 且 t 中无 k |
| `__newindex`（表或函数） | 写 `t.k` 且 t 中无 k |
| `__call` | `t(...)` |
| `__add __sub __mul __div __mod __pow __unm __idiv` | 算术 |
| `__band __bor __bxor __bnot __shl __shr` | 位运算 |
| `__concat` | `..` |
| `__len` | `#t` |
| `__eq __lt __le` | `== < <=`（`~=` 由 `==` 取反） |
| `__tostring` | `tostring(t)` |
| `__gc` | 表被 GC（仅 userdata/表 5.3 支持） |

`rawget(t,k)` / `rawset(t,k,v)` / `rawequal` / `rawlen(t)` 可绕过元表（调试 Java 对象、遍历代理时有用）。

### 2.7 协程（coroutine）

```lua
co = coroutine.create(function(a) local x = coroutine.yield(a+1); return x end)
coroutine.resume(co, 10)   --> true, 11
```

注意：pro 中协程与 **LuaThread（操作系统线程）是两套体系**；Java 对象跨协程是同一 LuaState，安全。协程在工程里较少见，线程需求用第 7 章的 `thread`/`task`。

### 2.8 错误处理

```lua
error("消息")                  -- 抛错
assert(cond, "消息")           -- 失败抛错
local ok, err = pcall(f, ...)  -- 捕获：ok=false 时 err 是错误对象
xpcall(f, 错误处理函数, ...)     -- 可拿到 traceback（工程用 debug.traceback 作处理函数）
```

- 宿主执行脚本时已自动包 `debug.traceback`，错误消息里自带 Lua 栈跟踪。
- Java 异常转成 Lua 错误后的消息文本见 4.9。

---

## 3. 标准库参考（Lua 5.3，全部可用）

### 3.1 基础库全局函数

| 函数 | 说明 |
|---|---|
| `print(...)` | AndroLua 中被替换：输出到宿主面板/Toast（见 5.2） |
| `type(v)` / `tostring(v)` / `tonumber(s[,base])` | 类型/字符串化/转数字 |
| `pairs(t)` / `ipairs(t)` / `next(t[,k])` | 遍历 |
| `select(n, ...)` / `select("#", ...)` | 第 n 个参数 / 参数个数 |
| `pcall(f,...)` / `xpcall(f,h,...)` | 保护调用 |
| `error(m[,level])` / `assert(v[,m])` | 抛错/断言 |
| `rawget/rawset/rawequal/rawlen` | 绕过元表 |
| `getmetatable/setmetatable` | 元表 |
| `load(s[,chunkname[,mode[,env]]])` / `loadfile` / `dofile` | 加载执行（工程脚本用 `loadstring` 旧别名亦可） |
| `collectgarbage(opt[,arg])` | GC 控制 |
| `require(name)` | 模块加载（见 4.2 的扩展加载器） |
| `_G` / `_VERSION` | 全局表 / `"Lua 5.3"` |

### 3.2 string 库（`s:method(...)` 或 `string.method(s,...)`）

| 函数 | 说明 |
|---|---|
| `byte(s[,i[,j]])` / `char(...)` | 字节 ↔ 码值（多返回值） |
| `len(s)` | 字节长度 |
| `sub(s,i[,j])` | 截取（负索引从尾部数） |
| `upper/lower(s)` / `rep(s,n)` / `reverse(s)` | 转换/重复/反转 |
| `find(s,pat[,init[,plain]])` | 查找，返回起止下标（plain=true 时无模式） |
| `match(s,pat[,init])` | 匹配，返回捕获 |
| `gmatch(s,pat)` | 迭代器 |
| `gsub(s,pat,repl[,n])` | 替换，返回新串+次数 |
| `format(fmt,...)` | C printf 风格（`%d %f %s %q %x %o`） |
| `dump(f)` | 函数转二进制字符串（字节码） |
| `pack/unpack/packsize` | 二进制打包（工程少用，HTTP 有原生封装） |

模式匹配（非正则）：`%a %d %s %w %l %u %p %x %c` 字符类、`%A` 补集、`^`/`$` 锚点、`* + - ?` 量词、`( )` 捕获、`%bxy` 平衡匹配（`%b()` 匹配括号对）。转义需写 `%%`。loadlayout.lua 的 `checkType`/`checkPercent` 即用模式解析 "12dp"/"30%w"。

### 3.3 table 库

| 函数 | 说明 |
|---|---|
| `table.insert(t[,pos],v)` / `table.remove(t[,pos])` | 数组插入/删除 |
| `table.concat(t[,sep[,i[,j]]])` | 拼接 |
| `table.sort(t[,comp])` | 排序 |
| `table.pack(...)` / `table.unpack(t[,i[,j]])` | 打包/解包（`unpack` 也可直接全局调用） |
| `table.move(a1,f,e,t[,a2])` | 批量搬移 |
| `#t` / `table.getn`（5.0 遗留，勿用） | 长度 |

### 3.4 math 库

`math.pi`、`math.huge`、`math.maxinteger`/`math.mininteger`（5.3 新增）；`abs ceil floor sqrt exp log log10`、`sin cos tan asin acos atan`、`deg rad`、`min max`、`random([m[,n]]) randomseed`、`fmod floor`、`tointeger(x)`、`type(x)`（`"integer"/"float"`）、`ult(a,b)`、`modf`。
注意 5.3 移除：`pow`（用 `x^y`）、`atan2`（用 `atan(y,x)`）、`ldexp/frexp`、双曲函数、`log` 第二位参数。

### 3.5 io 库

`io.open(path, mode)`（`"r"/"w"/"a"`、`"rb"` 二进制）、`io.lines/io.input/io.output`、`io.read(...)`、`io.write(...)`、`io.popen(cmd)`（**pro 的 os 扩展用这个执行 shell 命令**）、`f:read("a"/"l"/"n"/n)`、`f:write(...)`、`f:seek`、`f:close`、`f:lines`。
Android 沙箱内路径需为可访问路径（工程目录、sdcard）；宿主 `io` 未被改写。

### 3.6 os 库

`os.date([fmt[,t]])`（`%Y-%m-%d %H:%M:%S`、`*t` 表）、`os.time([t])`、`os.difftime`、`os.clock`、`os.getenv`、`os.execute`、`os.remove`、`os.rename`、`os.exit`、`os.tmpname`。
**pro 特例**：`import` 之后 `os` 表被挂上元表——`os.ls()`、`os.cat("/path")` 等会把键拼成 shell 命令经 `io.popen` 执行并返回输出（`resources/lua/import.lua` l.467-481）。已有键（如 `os.date`）优先于命令执行。

### 3.7 package 库与 require

- `package.path`：宿主注入为 `luaDir/?.lua; luaDir/lua/?.lua; luaDir/?/init.lua; ...`（LuaActivity l.238）。
- `package.cpath`：`nativeLibraryDir/lib?.so; libDir/lib?.so`（LuaApplication l.187）。
- `package.searchers`（5.3 的加载器数组）：pro 会追加两个：
  1. **libsloader**（import.lua l.17-26）：`libs/<name>.so` 的 `luaopen_xxx` 原生模块；
  2. **alyloader**（loadlayout.lua l.41-61）：`.aly` 布局文件（见 6.6）。
- `package.loaded`、`package.preload`、`package.loadlib`、`package.searchpath`。

### 3.8 debug 库

`debug.traceback([msg[,level]])`、`debug.getinfo`、`debug.getlocal/setlocal`、`debug.getupvalue/setupvalue/upvalueid/upvaluejoin`、`debug.sethook/gethook`、`debug.getregistry`、`debug.getmetatable/setmetatable`、`debug.getuservalue/setuservalue`。
`import.printstack()` 基于 debug 库实现（见 8.2）。

### 3.9 utf8 库（5.3 新增）

`utf8.char(...)`、`utf8.charpattern`、`utf8.codes(s)`、`utf8.codepoint(s[,i[,j]])`、`utf8.len(s[,i[,j]])`、`utf8.offset(s,n[,i])`。
注意：`string` 库函数按字节工作，中文处理用 utf8 库。

---

## 4. luajava 桥接层：Java 互操作语法

> 依据：`jni/luajava/luajava.c`、`java/com/luajava/LuaJavaAPI.java`、`LuaState.java`。
> 打开 `openLibs()` 后自动注册 `luajava` 全局表。

### 4.1 `luajava` 命名空间（13 个注册函数）

| 函数 | 签名（Lua 视角） | 行为 |
|---|---|---|
| `luajava.bindClass(name)` | `(string) → Class` | 按**点号全名**加载 Java 类；8 个基本类型名（`boolean byte char short int long float double`）映射到 `.TYPE`；失败抛 "Class not found: ..."。`$` 内嵌类直接用 `a.b.Outer$Inner` |
| `luajava.new(...)` | 见 4.5 | 全能构造/代理入口 |
| `luajava.newInstance(name, ...)` | `(string, args...) → 对象` | 按类名字符串构造 |
| `luajava.loadLib(class, method)` | `(Class, string)` | 调用类的**静态方法（签名为 `xxx(LuaState)`）**开原生库，返回结果个数 |
| `luajava.createProxy(interfaces, table)` | `(string, table) → 代理` | 接口名逗号分隔；table 键=方法名、值=Lua 函数；返回 JDK 动态代理（见 4.6） |
| `luajava.newArray(class, dims...)` | `(Class, int...) → 数组` | 创建多维数组（维度为后续参数） |
| `luajava.createArray(classname, table)` | `(string, table) → 数组` | 按表 1..n 填数组（字符串/基本类型有专化） |
| `luajava.astable(javaObj)` | `(Object) → table` | 数组/Collection → 顺序表；Map → 键值表；栈顶 `true` 则递归展开嵌套对象 |
| `luajava.tostring(obj)` | `(Object) → string` | `obj.toString()`，null → `"null"`；非对象值退回 Lua tostring |
| `luajava.coding(str[,from[,to]])` | `(string, string, string) → string` | 转码；**默认 GBK → UTF8**（luajava.c l.1183-1219） |
| `luajava.clear(obj)` | `(Object)` | 清缓存并释放全局引用（`gc`） |
| `luajava.instanceof(obj, class)` | `(Object, Class) → boolean` | 原生 `IsInstanceOf` |
| `luajava.getContext()` | `() → LuaContext` | 返回注册表中的宿主上下文（activity/service） |

另有宿主注入的**非函数字段**：`luajava.luadir`、`luajava.luapath`、`luajava.luaextdir`（字符串），`luajava.ids`（控件 id 表，见 6.5）、`luajava.loaded`/`luajava.imported`（已导入类表，import.lua l.5-6）。

**包链式访问**：`luajava` 表挂有 `__index` 元方法（import.lua l.485-499）——`luajava.android.widget` 会尝试 `luajava.bindClass("android.widget")`，失败则返回"包代理表"，继续下一段：`luajava.android.widget.Button` 等价 `luajava.bindClass("android.widget.Button")`，绑定结果被缓存。因此 `import` 之后可以写 `luajava.android.widget.Button(...)`。

### 4.2 类的表示：Class userdata 与 8 个基本类型全局名

- `bindClass` 返回的 `java.lang.Class` 对象以 userdata（metatable 名 `"JavaObject"`，标记字段 `__IsJavaObject`）压栈。Class 与普通实例**共用同一套索引规则**，靠运行时判断区分。
- `openLibs()` 自动把 8 个基本类型 Class 注册为全局变量：`boolean byte char short int long float double`（LuaState.java `pushPrimitive`）。因此 `int[10]` 可以创建 int 数组。

### 4.3 import：类导入机制（resources/lua/import.lua）

工程约定 `require "import"`（`activity`/`service` 初始化后通常自动 require，见 5.2），效果：

1. **`import "java.lang.String"`**：绑定类并赋给全局变量 `String`（名字取 `[^%.$]+$` 段）。同包多次 import 只绑一次，结果缓存于 `luajava.loaded`。
2. **`import "android.widget.*"`**：通配符 → 返回**包代理表**（元表 `__index` 按需绑定），可 `import "android.widget"` 后 `widget.Button` 或直接 `import "android.widget.*"` 后 `Button`？——注意通配形式本身只产生包表，仍须 `pkg.Class` 访问；而**全局自动解析**（下一条）会兜底。
3. **`import "dex名:包.类"`**：从工程 libs 的 dex/jar 加载类（`luacontext.loadDex(dexname).loadClass(...)`）。
4. **下划线转美元**：类名中的 `_` 自动变 `$`（`massage_classname`），兼容 "View_OnClickListener" 这类写法。
5. **全局名自动解析**（import.lua l.139-186）：`_G` 被挂元表 `__index`——任何未定义全局名依次尝试绑定 `类名`、`java.lang.类名`、`java.util.类名`、`com.androlua.类名`（含 dex 加载器）。所以 `TextView`、`LinearLayout`、`Toast` 等**不 import 也能直接用**（依赖 com.androlua/java 前缀）。未命中返回 nil。
6. import 同时自动导入 `loadlayout`、`loadbitmap`、`loadmenu` 为全局（import.lua l.204-206），并注入全局 `thread`、`task`、`timer`、`dump`、`printstack` 等（见 8.2）。

### 4.4 构造对象：`Class(...)` 与 `Object { ... }` 写法

`luajava.new` 是 `Class(...)` 调用的落地处（metatable `__call`），按参数分派（luajava.c l.887-934 + LuaJavaAPI.java l.640-716）：

| 调用形式 | 行为 |
|---|---|
| `Class(a, b, ...)` | **构造器调用**：按参数个数+类型逐个构造器匹配（`compareTypes` 评分），无参与先 `newInstance()` 失败则试 `(Context)` 构造器 |
| `Interface{ 方法 = 函数 }` | 接口代理（见 4.6） |
| `AbstractClass{ 方法 = 函数 }` | 抽象类动态子类（见 4.7，`javaOverride`） |
| `Class{ 表 }`（普通类，空表/数组表） | 表首元素可赋值 → 建数组，否则按构造器匹配 |
| `List{...}` / `Map{...}` / `int{...}` 等 | 建 List / Map / 数组 |
| `对象{ 属性表 }`（第一参是实例） | `objectCall`：LuaMetaTable 走 `__call`；否则把表灌进对象——List 追加、数组填、Map 放入、其余按 setter 写（LuaJavaAPI.java l.738-778） |

工程常用 `Object { ... }`（`java.lang.Object` 的数组化构造）把变参包成 Object[]（import.lua l.400、l.438 等）。

### 4.5 成员访问：读 `obj.member`

`obj.成员名` 走 `__index`，**解析顺序**（LuaJavaAPI.java l.89-119）：

1. **方法**（同名方法存在即命中，返回可调用函数，缓存）
2. **public 字段**（`getField`；final 缓存，非 final 每次重读）
3. **嵌套类**（`Outer.Inner` 式，匹配 `getClasses()` 的 simpleName）
4. **getter/is**：`getXxx()` / `isXxx()`（Map 特例：`map.get(key)`）
5. LuaMetaTable 的 `__index`

全未命中：Lua 报错 `"<tag> is not a field or mothod"`（源码原文拼写如此，luajava.c l.558）。

要点：
- **方法优先于字段**；同名方法返回的"函数"是 C 闭包，调用时完成重载解析。
- **静态成员**：`Class.成员` 只命中 static 方法/字段；实例方法调用 `实例:方法(...)` 或 `实例.方法(实例, ...)`。
- **重载解析**（LuaJavaAPI.java l.121-277）：先试参数数匹配 + 单参类型缓存（string/bool/integer/double 桶），再遍历全部同名方法按参数类型逐个试，**invoke 异常不判定失败继续试下一个**；全部失败抛 `"Invalid method call.\n at <方法> -> <原因>"` 或 `"Invalid Parameters."`。
- 数字键：数组/List/Map 下标（`arr[i]`、`list[1]`、`map["k"]`）；**Class 的数字键 = 建数组**（`int[10]` → 长度 10 的 int[]）。
- 链式：`view.getLayoutParams().gravity` 合法。

### 4.6 成员写入：`obj.member = v`

`__newindex` 解析顺序（LuaJavaAPI.java l.288-354 + l.1240-1478）：

1. **public 字段**（类型按字段转换）
2. **setter**：`setXxx(v)` 单参匹配（缓存）
3. **onXxx 监听器糖**：`obj.onClick = function` 且存在 `setOnClick...` 类方法 → 自动生成**接口代理监听器**（Java 接口方法名映射，见 4.7）
4. **私有/继承字段**（`setDeclaredFieldValue`，含 `m` 前缀字段名推断）
5. LuaMetaTable 的 `__newIndex`

数字键写 = 数组/List/Map 元素写。全失败报 `"<名字> is not a field"`。

### 4.7 接口代理与抽象类覆写

**接口代理（两种等价写法）**：

```lua
-- 方式一：createProxy（支持多接口，逗号分隔）
listener = luajava.createProxy("android.view.View$OnClickListener", { onClick = function(v) print("点击") end })

-- 方式二：luajava.new(接口, 表)（类调用糖）
listener = OnClickListener{ onClick = function(v) end }

-- 事件绑定两种等价形式：
view.setOnClickListener(listener)
view.onClick = function(v) end        -- setter 糖自动代理
```

代理规则（`LuaInvocationHandler`）：
- 方法名 → 表键取 Lua 函数；取不到按返回类型给默认值（boolean→false、数字→0、其余→null）。
- Lua 函数返回值经 `convertLuaNumber` 按**接口方法返回类型**转换（Double→int/long 等）。
- 回调里的 Lua 异常不向 Java 抛出，而是 `sendError` 上报（可被 `onError` 拦截）。

**抽象类覆写（`luajava.new(抽象类, 表)` → `javaOverride`）**：
- 用 cglib `LuaEnhancer` 生成子类，只覆写**表里出现的方法名**；实例上挂 `LuaMethodInterceptor`。
- Lua 函数签名带 super：`function(self, super, ...)`，`super(...)` 可调父类实现（`LuaMethodInterceptor.SuperCall`，LuaJavaAPI.java l.660-682）。
- 纯抽象类（无具体方法）也可走 `createAbstractProxy`（无 super 参数）。

**数组创建**：`luajava.createArray("java.lang.String", {...})`；多维 `luajava.newArray(int, 2, 3)`。

### 4.8 类型转换规则（双向，手册重点）

**Lua → Java**（`compareTypes`，LuaJavaAPI.java l.1660-1783）：

| Lua 值 | 目标 Java 类型 | 结果 |
|---|---|---|
| `nil` | 任意 | `null` |
| boolean | boolean/Boolean | 原值（其他类型报错） |
| string | String | 原串（UTF-8 字节经 JNI 转码） |
| number | byte/short/int/long/float/double 及其包装 | 按目标类型截断/转换（`convertLuaNumber`）；整数优先转 Long，浮点转 Double |
| function | 接口 | **动态代理**（自动把函数包装成单方法接口） |
| function | LuaFunction | 句柄 |
| table | LuaTable | 句柄 |
| table | 数组 | 逐项转换填数组 |
| table | List/Map | ArrayList / HashMap（或目标类型实例化） |
| table | 接口 | 代理（方法名→表键） |
| userdata(Java 对象) | 对应类型 | `isAssignableFrom` 校验；基本类型只收包装类 |
| userdata(非 Java) | LuaObject | 句柄 |

**Java → Lua**（`pushObjectValue`，LuaState.java l.1013-1049）：

| Java 值 | Lua 结果 |
|---|---|
| `null` | `nil` |
| Boolean | boolean |
| Long/Integer/Short/Character/Byte | integer |
| Float/Double | number |
| String | string（修改版 UTF-8） |
| byte[]（经 LuaString） | 二进制安全 string（`lua_pushlstring`） |
| JavaFunction | 可调用的 Java 函数 userdata |
| 其余（含数组、Map、List、普通对象） | **JavaObject userdata**（泛型 Lua 表由 `astable` 转） |

- **Java 字符串属性**：getter 返回 CharSequence 时直接压 Lua 字符串而非 userdata（LuaJavaAPI.java l.1232-1235），所以 `textView.getText()` 拿到的就是 string。
- **对象数量上限**：`pushJavaObject` 计数超 **50000** 抛 `RuntimeException("Out of JavaObject")`（LuaJavaAPI.java l.1818-1821）——长循环里不断 new 对象要注意复用/释放。
- `luajava.coding` 处理 GBK 等非 UTF-8 文本；JNI 侧 jstring 全部用修改版 UTF-8。

### 4.9 错误与异常模型

- **Java 异常 → Lua**：`checkError` 把 pending 异常清掉、取 `getMessage()` 文本（null 时取 `toString()`）后 `lua_error`。**无 Java 栈跟踪**，只有消息；`callMethod`/构造器错误会拼 `" at <方法>\n -> <原因>"` 达到方法签名级定位。
- **Lua 错误 → Java**：宿主统一 `pcall` + `debug.traceback`；Java 侧抛 `LuaException`，错误码文案：1 Yield / 2 Runtime / 3 Syntax / 4 OOM / 5 GC / 6 error error（`LuaThread.java` l.217-233）。
- 回调（代理/监听器/onXxx）内错误**不逃逸**：上报 `sendError(methodName, e)` 后返回默认值。

### 4.10 杂项语法

- `tostring(javaObj)` → `toString()`；`#javaObj` → `length()`（CharSequence/Collection/Map/数组）；`a == b`（双方 JavaObject）→ `equals()`。
- `luajava.instanceof(obj, Class)` 判类型。
- `luajava.astable(map)` 后可用 `pairs()` 遍历 Java Map/List。
- Java 集合类名带 `$`：`android.view.View$OnClickListener` 或 import 时下划线写法 `View_OnClickListener`。

---

## 5. 宿主环境：全局注入与 activity API

### 5.1 全局变量注入清单（LuaActivity.initLua，LuaActivity.java l.1442-1504）

| 全局名 | 值 |
|---|---|
| `activity` | 当前 LuaActivity 对象（userdata） |
| `this` | 同上（副本） |
| `print` | `LuaPrint`：各参数用 `\t` 包裹连接后 `sendMsg`（面板/Toast） |
| `set` | `set(线程, key, value)` → 往目标线程状态写全局 |
| `call` | `call(线程, 函数名, ...)` → 在目标线程状态调用函数 |
| `boolean byte char short int long float double` | 8 个基本类型 Class |
| `luajava` | 桥接命名空间（4.1） |
| `package.path / package.cpath` | 模块搜索路径（3.7） |

LuaService.initLua 注入 `service`（而非 activity），其余相同（LuaService.java l.486-587）。

### 5.2 启动流程与入口函数

```
LuaActivity.onCreate
 ├─ 取目录：luaDir(localDir)/odexDir/libDir/luaMdDir/luaCpath/luaLpath/luaExtDir
 ├─ 构造 LuaState → openLibs() → 注入全局（5.1）
 ├─ 加载工程 init.lua（独立 env 的 chunk，不是 require；env 表读 appname/theme/debugmode）
 ├─ LuaDexLoader 扫描 <luaDir>/libs/*.jar|*.dex|*.so 并 loadLib
 ├─ 执行目标脚本：doFile(main.lua 或 URI 指定脚本, arg)
 └─ 依次 runFunc("main", arg)、runFunc(页名, arg)、runFunc("onCreate", saved)
```

脚本内约定（官方 demo 顺序）：

1. `require "import"`（或依赖宿主自动 require —— 官方模板在脚本首行 require）
2. 定义入口函数 `function main(arg) ... end`（arg 为 `activity.getArg(n)` 数组或启动参数）
3. 用 `activity.setContentView("layout")` 或布局表构建 UI（第 6 章）
4. 定义生命周期回调（5.5）

**init.lua（工程元数据，独立 env）**：

```lua
appname = "应用名"                 -- 或 app_name；设置标题
appver = "1.0"
appcode = 1
packagename = "com.example.demo"
developer = "作者"
description = "描述"
debugmode = true                  -- 或 debug_mode
theme = 16973939                  -- 或 "Theme_Holo_Light"（android.R.style 字段名）
user_permission = { "android.permission.INTERNET" }   -- 打包 .alp 时用
```

### 5.3 `activity` 常用 API（LuaActivity 公共方法，均可直接 `activity.xxx(...)`）

**路径与资源**

| 方法 | 说明 |
|---|---|
| `getLuaPath()` / `getLuaPath(dir,name)` / `getQuery(name)` / `getArg(idx)` | 启动脚本路径 / Intent query / 参数数组 |
| `getLuaDir()` / `setLuaDir(dir)` | 工程目录（`activity.luaDir` 属性写法等价 getter） |
| `getLuaExtDir()` / `setLuaExtDir` / `getLocalDir()` / `getLuaLpath()` / `getLuaCpath()` | 扩展/本地目录、路径 |
| `getContext()` / `getLuaState()` / `getDecorView()` / `getWidth()` / `getHeight()` | 上下文/状态/根视图/尺寸 |
| `getAssets()` / `getResources()` / `getLuaResources()` | 资源访问 |
| `loadLib(name)` | 加载 libs/<name>.so 并 require |
| `readAsset(name)` / `assetsToSD(in,out)` / `unZipAssets(zip,dir)` | 资源文件操作 |

**数据**

| 方法 | 说明 |
|---|---|
| `getGlobalData()` | LuaApplication 内存数据 |
| `getSharedData(key)` / `getSharedData(key,def)` / `setSharedData(key,value)` | SharedPreferences 持久化（String/数字/布尔/Set/LuaTable） |
| `result(Object[])` | 向调用方 Activity 返回结果 |
| `setField(名, 值)` | 往 LuaState 写全局 |

**导航**

| 方法 | 说明 |
|---|---|
| `newActivity(path)` / `newActivity(path, {arg...})` | 打开另一 Lua 页面（相对路径自动补 main.lua/.lua）；arg 传给入口函数 |
| `newActivity(req, path, ...)` | 带 requestCode，返回后触发 `onResult` |
| `finish()` / `finish(true)` | 结束（true 连带任务栈） |
| `startService(...)` / `stopService()` / `bindService(...)` | 服务控制 |
| `setFragment(fragment)` | 挂 Fragment |
| `createShortcut(text, name[,icon])` / `shareFile(path)` / `installApk(path)` | 桌面/分享/安装 |
| `getUriForPath/getUriForFile/getPathFromUri` | URI 转换 |

**输出**

| 方法 | 说明 |
|---|---|
| `sendMsg(msg)` | 默认输出面板追加一行 |
| `sendError(title, msg)` | 先调 Lua 全局 `onError(title, msg)`，返回 true 则吞掉 |
| `showToast(text)` | Toast（1 秒内合并） |

### 5.4 线程/定时器（activity 版工厂）

| 方法 | 说明 |
|---|---|
| `newThread(func, args...)` / `thread(...)` | LuaThread（独立 LuaState 的 OS 线程），见第 7 章 |
| `newTask(func, args..., callback)` / `task(...)` | LuaAsyncTask（AsyncTask 池） |
| `newTimer(func, delay, period, args...)` / `timer(...)` | LuaTimer 循环定时器 |
| `ticker(func, period)` | 主线程 Handler 节拍器 |

### 5.5 生命周期与事件回调（Lua 全局函数约定）

| Lua 全局函数 | 触发时机 | 备注 |
|---|---|---|
| `main(arg)` / `<页名>(arg)` / `onCreate(saved)` | 启动（依次） | arg 数组 |
| `onStart/onResume/onPause/onStop` | 生命周期 | |
| `onDestroy()` | 销毁前 | 之后执行 GC |
| `onResult(name, data...)` | `newActivity` 返回 | 返回 true 则消费 |
| `onKeyDown/onKeyUp/onKeyLongPress(keyCode, event)` | 按键 | 返回 true 消费 |
| `onKeyShortcut(keyCode, event)` | 快捷键 | |
| `onTouchEvent(event)` | 触摸 | |
| `onCreateOptionsMenu(menu)` | 选项菜单 | 菜单用 `loadmenu`（6.7） |
| `onOptionsItemSelected(item)` / `onMenuItemSelected(id, item)` | 菜单项 | item.id 为 `luajava.ids` 里登记的 id |
| `onCreateContextMenu(menu, v, info)` / `onContextItemSelected(item)` | 上下文菜单 | |
| `onConfigurationChanged(config)` | 配置变化 | |
| `onReceive(context, intent)` | 广播（经 `activity.registerReceiver`） | |
| `onError(title, msg)` | Lua 错误 | 返回 true 吞掉错误 |
| `onAccessibilityEvent(...)` | 无障碍服务事件 | |
| `onHook()` | xposed 钩子探测后 | |

### 5.6 组件封装（com.androlua，Lua 侧直接构造）

| 类 | 用途要点 |
|---|---|
| `LuaDialog` | AlertDialog 封装：`LuaDialog(activity)`；`setTitle/setMessage/setView(布局)`；`setItems/setSingleChoiceItems/setMultiChoiceItems`；按钮 `setPosButton(text[,cb])`、`setNegButton`、`setNeuButton`、`setButton1/2/3`、`setOkButton`、`setCancelButton`、`setOnClickListener`；`getListView` |
| `LuaWebView` | WebView + JS 接口 `androlua`（`callLuaFunction(name[,arg])`、`doLuaString(src)`）；`setAdsFilter(fn)`、`setProgressBarEnabled`、下载到 `<luaExtDir>/Download/` |
| `LuaAdapter` | ListView 适配器：`LuaAdapter(context, 数据表, 布局表)`；`add/addAll/insert/remove/clear/getData/setStyle`；布局表按 6 章规则渲染每行 |
| `LuaArrayAdapter` | 简单文本/位图列表适配器 |
| `LuaBitmap` | 静态工具：`getBitmap(ctx,path)`、`getHttpBitmap`（7 天缓存）、`getLocalBitmap`、`getAssetBitmap`、`setCacheTime` |
| `LuaCameraView` / `LuaView` | 相机预览 / 自定义 View（表里 `onMeasure` 函数可覆写测量） |
| `LuaEditor` | 代码编辑器控件（textwarrior 内核，IDE 用） |
| `Http` | 静态 HTTP：`Http.get(url, cb)`、`Http.post(url, data, cb)`、`Http.download`、`Http.put`、`Http.delete`、`Http.setHeader/setUserAgent`；cb 收到响应字符串 |
| `Download` | 下载管理器封装 |
| `GifDecoder` | GIF 解码 |

---

## 6. UI 布局系统：loadlayout / loadmenu / loadbitmap

> 依据：`resources/lua/loadlayout.lua`（674 行）、`loadmenu.lua`、`loadbitmap.lua`。

### 6.1 布局表语法

```lua
require "import"
import "android.widget.*"
import "android.view.View"

layout = {
  LinearLayout,                     -- ① 首元素：控件类（userdata Class）
  layout_width = "fill",            -- ② 属性：键=属性名，值见 6.3
  layout_height = "fill",
  orientation = "vertical",
  gravity = "center",
  {                                 -- ③ 数字键：子视图（递归）
    TextView,
    id = "title",
    text = "你好",
    textSize = "20sp",
  },
  {
    Button,
    id = "btn",
    text = "点击",
    onClick = function(v) print("clicked") end,   -- 事件（4.6/6.4）
  },
}
activity.setContentView(layout)     -- 或 activity.setContentView("布局模块名")
```

- 首元素必须是 Class（`import` 后裸写类名，如 `LinearLayout`）；否则报错 `"loadlayout error: Fist value Must be a Class..."`。
- 子视图键可以是任意正数（工程惯用连续数字）；值为表（递归）或字符串（`require` 的布局模块名）。
- **字符串布局**：`activity.setContentView("layout")` 或 `loadlayout("模块名")` → `require` 该模块（其返回值须是布局表）；`.aly` 文件同路径解析（6.6）。
- 构建产物：根视图（view），可作为普通 Java 对象操作；`id="xxx"` 会同时创建全局变量 `xxx` 指向该控件。

### 6.2 根视图与子视图挂载

- 每次 `loadlayout` 返回根 view；嵌套时用 `parent.addView(child)`（自动）。
- AdapterView（ListView 等）的数字子视图生成 `LuaAdapter`：子表即**行布局**（6.5）。
- `ViewPager` 的 `pages = { ... }` 键生成 `ArrayPageAdapter`（6.5）。

### 6.3 属性值约定（checkValue，loadlayout.lua l.306-354）

| 写法 | 含义 |
|---|---|
| 数字 | 原样（像素值） |
| `"true"/"false"` | 布尔 |
| `"wrap"`/`"wrap_content"` | -2 |
| `"fill"`/`"match_parent"`/`"fill_parent"`/`"match"`/`"fill"` | -1 |
| `"12dp"`/`"20sp"`/`"10px"`/`"3pt"`/`"2in"`/`"5mm"` | `TypedValue.applyDimension` 换算 |
| `"30%w"` / `"30%h"` | 屏幕宽/高的百分比 |
| `"#ff0000"`（≤6 位） | 颜色（转 Android 0xAARRGGBB；8 位 `"#aarrggbb"` 原样） |
| `"center"`/`"top"`/`"left"`/`"bottom"`/`"end"`/`"start"` 等 | 命名常量（Gravity/visibility/orientation/scaleType/inputType/imeOptions 等，loadlayout.lua `toint` 表） |
| `"a|b|c"` | 常量按位或组合（如 `"top|center_horizontal"`） |
| `"?资源名"` | 资源 id（`getIdentifier`，如 `"?android:attr/selectableItemBackground"`） |
| 其他字符串 | 原样字符串 |

**颜色注意**：`"#ff0000"` → `0xff0000 - 0x1000000 = 0xffff0000`（即 ARGB 全不透明红色）。8 位十六进制带符号处理。

### 6.4 特殊属性键

| 键 | 行为 |
|---|---|
| `id` | 生成全局变量 + `luajava.ids[id]=控件id` + `view.setId` |
| `style` | 字符串 → require 模块（其表作为该布局的元表 `__index`，即"样式继承"）；`"?资源名"` → 主题样式资源；否则 `android.R.style` 名 |
| `items` | 表/函数/字符串 → 填充 ListView：`ArrayListAdapter(context, android.R.layout.simple_list_item_1, String(数据))`；已有 adapter 则 `addAll` |
| `pages` | ViewPager 页：数字键表 → 每项 `loadlayout` 后 `ArrayPageAdapter` |
| `layout_weight` / `layout_x` / `layout_y` / `layout_gravity` | 直接写 LayoutParams 字段 |
| `layout_margin*` / `padding*` | 边距/内边距（含 Start/End） |
| `layout_align*` / `layout_toLeftOf` / `layout_below` 等（RelativeLayout） | `rules` 表（loadlayout.lua l.223-247）→ `params.addRule`；值为 true 则锚父边，字符串则锚 `ids[名字]` 的控件 |
| `textSize` | 数字（sp 默认）或 `"20sp"` |
| `textAppearance` | `setTextAppearance`（支持 `?attr` 名） |
| `ellipsize` | `"end"/"middle"/"start"/"marquee"` → `TruncateAt` |
| `url` | `view.loadUrl(url)`（WebView） |
| `src` | `"?资源"` → setImageResource；`http(s)://` → 异步 loadbitmap 后 setImageBitmap；否则 loadbitmap 本地路径 |
| `scaleType` | `matrix/fitXY/fitStart/fitCenter/fitEnd/center/centerCrop/centerInside` |
| `background` | `"#色值"` → setBackgroundColor；`"?资源"` → setBackgroundResource；函数 → `LuaDrawable(fn)` 自绘；userdata → 直接 setBackground；路径 → `LuaBitmapDrawable`（`.9.png` → `NineBitmapDrawable`） |
| `onClick` | 函数 → `OnClickListener{onClick=fn}` 代理；字符串 → 取全局/根表函数；userdata → 直接设置；同名缓存（`ltrs`） |
| `password` | `true/"true"` → `setInputType(0x81)` |
| 其他 `xxx` | 首字母大写 → `view.setXxx(value)`；表值 → 展开成多参（`padding={10,20}` → `setPadding(10,20,...)`）；`Text/Title/Subtitle/Hint` 特判直接 `set` 系列 |

任何 setter 参数都过 `checkValue`，所以 `textSize="20sp"`、`gravity="center"` 等字符串值自动换算。属性设置失败会被 `pcall` 捕获并打印 `loadlayout error ...`（不中断布局）。

### 6.5 列表行布局（LuaAdapter 行渲染）

`LuaAdapter(context, data, rowlayout)`：数据表（1..n）每项与行布局表配套——行控件 `id="title"` 时，数据项 `{ title = "行1标题", ... }` 自动 `setText`；控件是 ImageView 时支持 Bitmap/字符串路径/资源 id/`loadbitmap` 的 AsyncLoader（自动加载+缓存）。行内 `onXxx` 函数自动代理为监听器。

### 6.6 `.aly` 布局文件

- `.aly` 是**纯表字面量的 Lua 源码**（`return { ... }` 或裸表），经 `alyloader`（package.searchers 之一）`loadstring("return " .. 内容)` 加载；`#!lua` 头或已编译字节码（`\27Lua` 魔数开头）则直接 loadfile。
- `require "xxx.aly"` 或字符串布局 `"xxx"` 命中同一搜索路径（`package.path` 的 `.lua` 换成 `.aly`）。
- 因此 `.aly` 里不能用函数值（不能有 `onClick = function`），是纯声明式布局；动态行为写在主脚本。

### 6.7 loadmenu（菜单）

```lua
function onCreateOptionsMenu(menu)
  loadmenu(menu, {
    { MenuItem, title = "保存", icon = "save", id = "save_item" },  -- icon 经 loadbitmap
    { MenuItem, title = "更多", group = 1, order = 2 },
    { SubMenu, title = "子菜单",
      { MenuItem, title = "子项1" }, ... },
  })
end
```

- 数字键顺序即显示顺序；前 n 个（`loadmenu(menu, t, root, n)` 的 n 参数）`setShowAsAction(SHOW_AS_ACTION_ALWAYS)` 进 actionbar。
- `id="名字"` 生成全局变量 + `luajava.ids` 登记，`onOptionsItemSelected(item)` 里 `item.getId() == ids.名字`（或直接 `if item == save_item`）。
- `enabled=false` / `visible=false` 可设。

### 6.8 loadbitmap（图片加载）

```lua
bmp = loadbitmap("icon")            -- 相对 luaDir，自动补 .png
bmp = loadbitmap("res/logo.png")
bmp = loadbitmap("http://example.com/a.png")   -- 7 天磁盘缓存
```

规则（loadbitmap.lua）：无扩展名自动补 `.png`；`http(s)://` 走 `LuaBitmap.getHttpBitmap`；相对路径 = `luajava.luadir/路径`；`/` 开头按绝对路径。返回值是 Bitmap userdata，可赋给 `src`/`background`/`icon` 或直接操作。

### 6.9 `luajava.ids`

`luajava.ids` 是全部 `id=` 登记的控件 id 表：`luajava.ids.名字 = 整数 id`，从 `0x7f000000` 起递增。`luajava.ids[字符串]` 也存（`ids[v]=id`）。

---

## 7. 异步编程：thread / task / timer

> 依据：`LuaThread.java`、`LuaAsyncTask.java`、`LuaTimer.java`、`import.lua` 封装。**每个线程都是独立 LuaState**，全局变量不共享。

### 7.1 thread（LuaThread，独立 LuaState 的线程）

```lua
require "import"
local th = thread(function()
    -- 子线程 LuaState：注入 activity/service、print、set/call
    -- 与主线程状态不共享变量；通过 call/set 通信
    return 42
end)
```

- `thread(src, ...)`：src 可为函数（dump 字节码）、脚本路径（相对 `activity.luaDir` 自动补全）、脚本源码。
- 返回值对象 `th` 支持（LuaThread 实现 LuaMetaTable）：
  - `th.call("函数名", args...)` / `th.set("键", 值)` / `th.get("键")` / `th.quit()` —— 经 Handler 在**子线程状态**内执行；
  - 语法糖：`th.f(x)` 等价 `th.call("f", x)`；`th.k = v` 等价 `th.set("k", v)`（import.lua l.397-419 的元方法实现）。
- 全局 `set(线程, 键, 值)` / `call(线程, 函数, ...)` 是 JavaFunction（LuaActivity l.1470-1502），用于**主线程向任意线程状态**写入/调用。
- 子线程可反复 `th.call`；主线程回调子线程的状态存取都是消息队列串行，天然无竞争。

### 7.2 task（LuaAsyncTask，AsyncTask 线程池）

```lua
task([[require "import"; url=...; return loadbitmap(url)]], url, function(bmp) image.setImageBitmap(bmp) end)
task(function() return 重活() end, function(ret) print(ret) end)
```

- `task(src, args..., callback)`：最后一个参数是回调（主线程执行）；结果经 `callback(result)`。
- `task` 内 `update(...)` 可向回调推送进度（LuaAsyncTask 注入的 JavaFunction）；`task` 返回 LuaAsyncTask 对象，可 `cancel()`。
- 子任务自动继承 `import` 过的类（快照 `luajava.imported` 逐类重新 import，LuaAsyncTask.java l.64-69、152-163）。

### 7.3 timer（LuaTimer，java.util.Timer 封装）

```lua
local t = timer(function()
    print("tick")
end, 0, 1000)          -- timer(f, delay, period, args...)：delay 毫秒后每 period 毫秒一次
t.stop()               -- t.start(delay[,period]) / setPeriod / setEnabled / gc
```

- 每次触发在**独立 LuaState** 上执行：首次执行脚本，之后若脚本定义了全局 `run` 则每次 `runFunc("run")`，否则重新执行整个脚本（LuaTimerTask.java l.52-88）。
- `period=0` 表示只执行一次（`start(delay)`）。

### 7.4 主线程节拍器

```lua
activity.ticker(function() 更新UI() end, 100)   -- 每 100ms 主线程回调
```

### 7.5 线程通信小结

| 场景 | 写法 |
|---|---|
| 子线程拿主线程值 | 构造时经闭包/arg 传入（`thread(f, a, b)` 的 a、b 随 `Object{...}` 传入） |
| 主线程调子线程 | `th.call("f", ...)`、`th.set(k, v)`、`call(th, "f", ...)`、`set(th, k, v)` |
| 子线程回主线程 UI | `activity.runOnUiThread`（Android API）、task 回调、`activity.sendMsg`（Handler 安全） |
| 子线程拿另一个子线程 | 同上 set/call 全局函数 |

---

## 8. import 模块附加函数

`require "import"` 之后（宿主首行惯例）可用：

| 函数 | 说明 |
|---|---|
| `import(package[,env])` | 显式导入（4.3）；`import{"a.B","b.C"}` 批量 |
| `import.compile("dex名")` | 加载 dex 类加载器到链 |
| `import.enum(枚举Enumeration)` | 枚举 → Lua 迭代器 |
| `import.each(迭代器)` | Java Iterator → Lua 迭代器 |
| `import.dump(obj)` | 递归打印 Lua 值/表（含循环引用检测、nil 哨兵、_G 引用显示），返回字符串 |
| `import.printstack()` | 打印当前 Lua 调用栈（debug 库实现，含 upvalue/local 快照） |
| `import.getids()` | 返回 `luajava.ids` |
| `import.thread(src, ...)` / `import.task(src, ...)` / `import.timer(f, d, p, ...)` | 7 章工厂 |
| `import.print(...)` | activity 存在时重定义 print：参数 `\t\t` 连接后 `activity.sendMsg` |

---

## 9. 运行时脚本库（resources/lua，25 个模块参考）

| 模块 | require 名 | API | 说明 |
|---|---|---|---|
| import | `"import"` | 见第 4、8 章 | 类导入 + 全局自动解析 + 并发工厂（自动注入全局） |
| loadlayout | `"loadlayout"` | `loadlayout(t[,root[,group]])` | 布局系统（第 6 章），自动注入全局 |
| loadbitmap | `"loadbitmap"` | `loadbitmap(path)` | 图片加载（6.8），自动注入全局 |
| loadmenu | `"loadmenu"` | `loadmenu(menu, t[,root[,n]])` | 菜单（6.7），自动注入全局 |
| json | `"json"` | `json.encode(t)`、`json.decode(s)`、`json.null()` | JSON4Lua 0.9.20（encode 紧凑；decode 支持注释/单双引号） |
| base64 | `"base64"` | `base64.encode(s)`、`base64.decode(s)` | BASE64 |
| hex | `"hex"` | `hex.dump(v[,delim])`、`hex.smart_dump`、`hex.pack`、`hex.smart_pack` | 十六进制显示/打包 |
| http | `"http"` | `http.get(u[,cok[,ua[,hdr]]])`、`http.post(u, data[,cok[,ua[,hdr]]])`、`http.upload(u,d,f,...)`、`http.download(u,p,...)`、`http.open` | LuaSocket HTTP 客户端（同步，阻塞调用） |
| ftp | `"ftp"` | `ftp.open(server[,port[,create]])` → conn：`login(user,pass)`、`cwd`、`type`、`send/put`、`receive/get`、`quit/close` | LuaSocket FTP |
| smtp | `"smtp"` | `smtp.open(...)`、`smtp.send{from=, rcpt=, source=, ...}` | LuaSocket SMTP |
| socket | `"socket"` | `socket.tcp()` / `socket.udp()` / `socket.connect(host,port)` / `socket.sleep` / `socket.select` | LuaSocket 核心（socket 目录：headers/tp/url 为配套模块） |
| mime | `"mime"` | `mime.b64/decode、qp、encode/wrap` | MIME 编解码 |
| ltn12 | `"ltn12"` | filter/source/sink/pump 体系 | 流式管线（HTTP 依赖） |
| mbox | `"mbox"` | `mbox.split_message` 等 | 邮件解析 |
| bmob | `"bmob"` | `bmob.new(appid,key)` → 表：`query/insert/update/increment/delete/upload/remove/sign/login` | 依赖 `cjson`（未内置时不可用） |
| logcat | `"logcat"` | 直接执行读取 logcat 的完整 UI 脚本 | IDE 工具 |
| console | `"console"` | `console.format(源码)`、`console.build(path)`、`build_aly(path)` | 脚本/aly 构建器（IDE 工具） |
| check | `"check"` | `check.reg(env,id,key,field)`、`check.new/final/check/uncheck` | 表单校验助手 |
| permission | `"permission"` | 执行即检查并请求权限的脚本（`android.permission.*`） | 工程模板用 |
| su | `"su"` | `su("cmd")` → 返回 root 命令输出 | root 执行 |
| autotheme | `"autotheme"` | `autotheme()` → 主题 id（按时间黑白） | 模板用 |
| bin | `"bin"` | `bin(path)` → APK 打包对话框 | IDE 打包工具 |
| test | `"test"` | 函数性能测试脚本（`test.run(f)` 等） | IDE 工具 |
| options | `"options"` | OpenSSL options 生成（纯工具，工程无关） | 预留 |
| xml | `"xml"` | 依赖 `xml.core`（LuaXML），模块导出 TAG 常量 | 未内置 xml.core 时 require 失败 |

> `ftp/smtp/http/socket/mime/ltn12` 是标准 LuaSocket 5.3 兼容版（源码同 Diego Nehab 原版），与官方文档一致；Android 上网络需 `user_permission` 含 `android.permission.INTERNET`。

---

## 10. 工程结构与规范

```
/storage/emulated/0/AndroLua/project/<name>/
├── init.lua      # 元数据（5.2），可选
├── main.lua      # 入口（必选；或 service.lua 服务入口）
├── icon.png      # 图标
├── libs/         # 工程库：*.jar / *.dex / lib<fn>.so（.alp 导出携带）
└── 其他 .lua / .aly / 资源
```

- **main.lua 模板**：

```lua
require "import"
import "android.app.*"
import "android.widget.*"
import "android.view.View"

activity.setContentView("layout")     -- 或直接布局表
-- 事件/逻辑...
function main(arg) ... end            -- 可选入口
```

- **多页面**：`activity.newActivity("page2")` 打开 `page2.lua`；`activity.newActivity("dir/name")` 自动补 `main.lua`/`.lua`；目标脚本可用 `function main(arg)`、`function onCreate(saved)`，返回用 `activity.result({...})`，接收方在 `onResult` 中收。
- **服务**：`service.lua` 由 `LuaService` 运行，全局是 `service`；`activity.startService` 启动；`onStartCommand`/`onStart` 回调。
- **打包**：`.alp` 包含脚本+init.lua+libs；bin.lua 提供 IDE 内打包。

---

## 11. 已知问题与规避（工程侧参考）

1. **全局变量污染**：不写 `local` 的变量都是全局；多个 Lua 页面/线程间**不共享**全局（独立状态）。页面内闭包才是隔离手段。
2. **`or` 默认值**：`x = x or v` 时 `false`/`0` 也会被替换；判空用 `x == nil`。
3. **`#` 长度**：对带空洞的表不可靠；Java 对象 `#` 是 length()。
4. **对象上限**：单状态 pushJavaObject 累计超 50000 抛 "Out of JavaObject"（4.8）。
5. **编码**：jstring 走修改版 UTF-8；GBK 文本先 `luajava.coding(s, "GBK")`。
6. **重载调用失败**：报 "Invalid Parameters." 时检查参数类型（number 的整/浮、表 vs 数组 vs Map）；数字型参数传 `math.floor` 或 `0` 后缀浮点可调整匹配。
7. **回调错误静默**：监听器/代理内 Lua 异常只上报不抛出（4.7），务必在回调里 `pcall` 处理或依赖 `onError` 排查。
8. **线程状态隔离**：thread 内拿不到主线程局部变量/控件引用（除非经 `th.set`/闭包参数传入）；UI 操作必须在主线程。
9. **timer 脚本反复执行**：timer 首次运行脚本、之后若定义 `run` 只调 `run`；把状态放全局变量会被重置。
10. **`os` 表被改写**：import 后 `os.ls()` 等键是 shell 命令执行器（3.6），别给 os 表加无关键。
11. **`toint` 常量表键冲突**：不同属性同名字段共用常量表（如 `inherit` 在 textAlignment/layerType/layoutDirection 都是 0），跨属性时注意。
12. **`activity.luaDir` 等属性**走 getter 反射（`getLuaDir`），字段本身 private 不可直接写；写路径会先试 public 字段再 setter（`setLuaDir` 存在，可写）。
13. **`.aly` 不能含函数**：纯表字面量（6.6）。
14. **中文在 `string` 库按字节**：`string.sub` 会切坏 UTF-8，用 `utf8` 库。
15. **`print` 输出目的地**：activity 场景进默认面板/Toast（`sendMsg`），不是 logcat；查 logcat 用 `adb logcat -s lua`。

---

## 12. 附录

### 12.1 关键错误消息文本（来自源码，便于 grep 定位）

| 消息 | 来源 |
|---|---|
| `"%s is not a field or mothod"`（拼写如此） | luajava.c l.558 |
| `"%s is not a field"` | luajava.c l.699-700 |
| `"Invalid object index. Must be integer or string."` | luajava.c l.562 |
| `"Invalid JNI Environment."` | luajava.c l.170 |
| `"Invalid Parameters."` / `"Invalid method call.\n at ..."` | LuaJavaAPI.java l.270-274 |
| `"Class not found: ..."` | LuaJavaAPI.java l.604-605 |
| `"Out of JavaObject"` | LuaJavaAPI.java l.1819-1820 |
| `"loadlayout error: Fist value Must be a Class..."` / `"...Must be a table..."` | loadlayout.lua l.585/604 |
| `"cannot find <包>"` | import.lua l.133 |

### 12.2 Lua 5.3 相对 5.1/5.2 的差异（工程写法影响）

- 新增整数子类型（`//` 整除、`math.tointeger`）、位运算符 `& | ~ << >>`（`~` 一元取反）、`goto`、`\u{...}`、`utf8` 库、`table.move`。
- `math.pow/atan2/ldexp/frexp/双曲` 移除；`unpack` → `table.unpack`；`loadstring` → `load`。
- 字符串与数字比较不再隐式转换（`"10" < 2` 报错）。
- 浮点 `for i=1, 0.5` 步长仍支持。
- 5.3 的 `string.format("%d")` 只收整数，`math.floor` 后传。

### 12.3 本手册对应源码索引（便于复核）

| 主题 | 源码位置 |
|---|---|
| luajava 注册函数 | `jni/luajava/luajava.c` l.1434-1464 |
| 元方法/缓存/重载 | `jni/luajava/luajava.c` l.455-708；`java/com/luajava/LuaJavaAPI.java` l.89-354 |
| 类型转换 | `java/com/luajava/LuaState.java` l.1013-1049、l.1058-1085；`LuaJavaAPI.java` l.1660-1783 |
| 代理/覆写 | `LuaJavaAPI.java` l.660-736；`LuaInvocationHandler.java`；`LuaMethodInterceptor.java` |
| 全局注入 | `java/com/androlua/LuaActivity.java` l.1442-1504 |
| 生命周期回调 | `LuaActivity.java` l.869-1051 |
| 线程/定时器 | `LuaThread.java`、`LuaAsyncTask.java`、`LuaTimerTask.java` |
| import 机制 | `resources/lua/import.lua` |
| 布局系统 | `resources/lua/loadlayout.lua` |
| 菜单/图片 | `resources/lua/loadmenu.lua`、`loadbitmap.lua` |
| Lua 规范 | `assets/luadoc/manual.html`（官方 Lua 5.3 手册中文版） |

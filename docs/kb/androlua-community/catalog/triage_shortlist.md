# Triage 短名单（v2：全量正文静态初筛 → 人工复核）

仅静态规则初筛，`static_review`；不代表设备行为。

## A. 建议入选 positive 的主题候选（987）

| id | 分类 | 标题 | 端口 |
|---|---|---|---|
| 3435 | 布局教程 | 一个简单的md风格主页界面布局v3 | android_api |
| 1875 | 布局教程 | 启动图 | android_api |
| 3408 | 小工具 | 关于一个简易关键词搜索功能的实现 | android_api |
| 2228 | 布局教程 | 两个仿MIUIDialog （三次优化） | android_api |
| 1294 | Adapter容器 | ListView列表适配器 | universal |
| 4938 | Lua教程 | 图集 | android_api |
| 2489 | 布局教程 | 海纳微句用启动图（仿闲言重制） | universal |
| 1531 | UI控件 | Slider | android_api |
| 2425 | 布局教程 | 软件设置界面布局，好看的按钮点击特效 | android_api |
| 3784 | 布局代码 | 好看的个人主页 | android_api |
| 4275 | UI控件 | 双层边框 | android_api |
| 3189 | 小工具 | 简易倒计时(3.2.1) | android_api |
| 1280 | UI控件 | EditText输入框控件 | android_api |
| 1994 | UI控件 | PullingLayout刷新控件 | universal |
| 4125 | 消息通知 | 独立消息显示系统html+lua | android_api |
| 2880 | 布局教程 | 超好看的登录界面（第三次修改）（卡片自绘制轻阴影） | android_api |
| 2479 | 实用代码 | 本地网页抽奖 | android_api |
| 1412 | 布局教程 | 轮播图 | android_api |
| 5140 | Lua代码 | Aurora Toast Lua | android_api |
| 1397 | UI控件 | Switch开关控件 | android_api |
| 4144 | 基础代码 | 一个简单的可推动侧滑栏 | android_api |
| 3448 | 布局教程 | ViewPager与TabLayout联动v2 | android_api |
| 4197 | Layout布局 | 关于丝滑这件事 | android_api |
| 1401 | Adapter容器 | ExpandableListView折叠列表 | universal |
| 686 | 控件属性 | 侧滑页面效果 | universal |
| 2805 | 实用代码 | Materializer | android_api |
| 2137 | 网络请求 | okHttp请求封装函数 | android_api |
| 4552 | 安卓教程 | Shizuku对接 | android_api |
| 1748 | 小工具 | 快手随机小视频 | android_api |
| 4056 | Lua教程 | 效率不高的『3d建模』--二元函数 3d 绘图器 | android_api |
| 2251 | 布局教程 | 美化PopMenu实例 | android_api |
| 3651 | Activity活动 | 一个简洁美观的氚布局（二次优化） | android_api |
| 4291 | 实用代码 | 比较优美的验证码输入页 | android_api |
| 1868 | 小工具 | 罗盘时钟 | android_api |
| 3440 | Activity活动 | 仿MIUI系统更新主界面检查更新布局v3 | android_api |
| 3037 | 布局教程 | 如何使用RecyclerView | android_api |
| 1174 | 布局教程 | 顶部下拉弹窗 | android_api |
| 3117 | 安卓教程 | 控件随手指移动 | universal |
| 2688 | 实用代码 | 简单使用白嫖的腾讯防水墙（可疑版） | android_api |
| 3006 | Intent通信 | QQ登陆 | android_api |
| 4090 | 手册公告 | 朽木、封杀，此子不可留也 | universal |
| 2254 | Intent通信 | 申请所有文件访问权限 | android_api |
| 4602 | 基础代码 | AndroLua常见的事件归纳存档V2 | android_api |
| 2673 | 布局教程 | 超好看UI | universal |
| 3446 | Activity活动 | ViewPager绑定TabLayout的两种方法 | android_api |
| 4118 | 实用代码 | 获取WiFi局域网内支持DLNA投屏的设备 | universal |
| 1292 | Layout布局 | DrawerLayout侧滑栏 | android_api |
| 4810 | Lua代码 | 流体浅变 | android_api |
| 1334 | UI控件 | ImageView图片控件 | android_api |
| 4723 | 网络爬虫 | 汽水音乐 api | universal |

## B. 建议入选 negative 的候选（680）

| id | 分类 | 标题 | 理由(flags) |
|---|---|---|---|
| 2183 | 小工具 | 中文函数 | incomplete_mark,androlua_private,private_api,chinese_identifier,incomplete_fragment,os_exec,sensitive_permission |
| 2515 | 布局教程 | 兴许是一个简洁美观的主页布局 | chinese_identifier |
| 1591 | 布局教程 | 可展开卡片(适配手册夜间模式) | editor_bound,editor_bound,chinese_identifier |
| 1418 | 小工具 | 网易云音乐（十三次优化） | androlua_private,private_api,chinese_identifier |
| 2721 | 小工具 | 蓝奏云直链解析(官方Api解析+密码解析)_v7.3 | androlua_private,private_api,remote_exec |
| 1301 | Adapter容器 | RecyclerView列表视图 | androlua_private,private_api |
| 1467 | 动画教程 | RecycleView滑动动画‮ | androlua_private,private_api,chinese_identifier |
| 3140 | 实用代码 | 用mediaPlayer构建视频播放器（四次迭代） | incomplete_mark,obsolete,obsolete,chinese_identifier,global_pollution,incomplete_fragment |
| 1360 | 浏览器 | WebView各种下载：直链下载，网页下载，下载进度监听等 | androlua_private,private_api |
| 1616 | 实用代码 | 动画封装函数等 | androlua_private,private_api,chinese_identifier |
| 3838 | 布局教程 | 一个BottomSheetBehavior布局 | androlua_private,private_api |
| 1668 | 布局教程 | 仿md3的switch | editor_bound,editor_bound,chinese_identifier |
| 2868 | 实用代码 | Banner ViewPager2 无限轮播 | androlua_private,private_api,remote_exec |
| 3008 | 布局教程 | 仿LSPosed主页布局 | editor_bound,editor_bound,chinese_identifier |
| 1520 | 布局教程 | 仿md3 NavigationBar | editor_bound,editor_bound,chinese_identifier,global_pollution |
| 2397 | UI控件 | SoraEditor编辑器 | androlua_private,private_api,chinese_identifier |
| 2266 | 布局教程 | 简函主页布局 | androlua_private,private_api |
| 1460 | 布局教程 | 海纳云用卡片式布局 | chinese_identifier |
| 5009 | UI控件 | EXOPlayer视频播放器 | chinese_identifier |
| 4944 | 实用代码 | 基于media2的新一代视频播放器 | androlua_private,private_api,sensitive_permission |
| 4558 | 网络爬虫 | 影视分类筛选页面 | androlua_private,private_api,chinese_identifier |
| 1524 | 布局教程 | Drawer | editor_bound,editor_bound,chinese_identifier |
| 3294 | 网络爬虫 | Fan - 一个追番的简易示例Demo(xpath) | androlua_private,private_api,global_pollution |
| 3047 | 文件操作 | 手册文件管理 | androlua_private,private_api,chinese_identifier,global_pollution |
| 4508 | 网络爬虫 | 高清手机壁纸fastjson解析json教程 | androlua_private,private_api,chinese_identifier |
| 1746 | 小工具 | 音乐+歌词 | androlua_private,private_api,remote_exec |
| 3023 | 小工具 | GPT API 调用，支持网络搜索，图片显示 | androlua_private,private_api,remote_exec |
| 1498 | 小工具 | 海纳云用视频播放器（三次优化） | androlua_private,private_api,chinese_identifier,global_pollution |
| 3459 | 安卓教程 | tab,关联page2 | androlua_private,private_api,sensitive_permission |
| 1349 | 实用代码 | Glide图片加载框架 | androlua_private,private_api |
| 2007 | 控件属性 | 底栏BottomNavigationView滑动隐藏 | androlua_private,private_api |
| 1565 | 布局教程 | 主页轮播图-重构版 | androlua_private,private_api |
| 2189 | 安卓教程 | ProgressBar | chinese_identifier,global_pollution |
| 3053 | 小工具 | 一个注册登录布局对接水仙后台实例 | androlua_private,private_api,chinese_identifier |
| 1819 | 小工具 | 天气Demo | editor_bound,editor_bound,chinese_identifier,sensitive_permission |
| 2129 | 网络操作 | API接口大全及使用 | androlua_private,private_api |
| 2315 | 小工具 | 应用管理器（整合修改） | androlua_private,private_api,chinese_identifier,sensitive_permission |
| 3104 | 小工具 | 一言 Demo | chinese_identifier |
| 2330 | 控件属性 | 一个列表分类布局 | androlua_private,private_api |
| 4696 | 安卓教程 | 音乐通知栏(正确取消广播监听 三次修复) | chinese_identifier |
| 4916 | Layout布局 | loadlayout | androlua_private,private_api |
| 4064 | 网络请求 | 蓝奏云 LanzouyunAPI 封装版 | androlua_private,private_api |
| 2967 | Intent通信 | QQ登录（修改了，可行） | chinese_identifier |
| 4210 | 网络爬虫 | 豆瓣2026热播排行榜 | androlua_private,private_api,chinese_identifier |
| 1877 | 系统 | 常驻通知&刷屏通知 | editor_bound,editor_bound,chinese_identifier |
| 4195 | 动画教程 | 丝滑的缓冲seekBar | chinese_identifier,sensitive_permission |
| 3014 | 布局教程 | 仿哔哩哔哩的视频播放布局 | chinese_identifier,global_pollution |
| 2523 | Java教程 | 爱发电赞助页 | androlua_private,private_api,sensitive_permission |
| 3861 | 设备系统 | PageTransformer 修改 ViewPager 滑动动画V2 | androlua_private,private_api |
| 2811 | 小工具 | 海纳云用内置图片取色器 | androlua_private,private_api,chinese_identifier |

## C. harmful 候选（8，仅识别，不用于工程）

- id 4460 | 基础代码 | 改 · 一键爬取iMyShare工具站工具V2 | androlua_private,private_api,harmful_elevated,remote_exec,sensitive_permission
- id 2276 | JS教程 | 混合加密(不可逆)卡密验证-密文 | manual_override,harmful_elevated
- id 2146 | 小工具 | 蓝奏云解析，原作者GitHub@nwdgzs | manual_override,harmful_elevated
- id 1598 | 小工具 | 仿Momo | androlua_private,private_api,chinese_identifier,harmful_elevated
- id 4697 | 基础代码 | 云注入模块 | androlua_private,private_api,manual_override,harmful_elevated
- id 2275 | JS教程 | 混合加密(不可逆)卡密验证-明文 | manual_override,harmful_elevated
- id 2274 | JS教程 | 混合加密(不可逆)-卡密生成 | chinese_identifier,manual_override,harmful_elevated
- id 2826 | Androlua教程 | 获取现在运行的是什么程序 | chinese_identifier,manual_override,harmful_elevated

> 说明：人工复核后如需调整 verdict，编辑 post_<id>.json 顶部 verdict/flags 并回写来源。

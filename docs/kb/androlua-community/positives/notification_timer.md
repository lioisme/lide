# 正向主题卡：通知与定时任务（NotificationChannel / LuaTimer / Ticker）

> reviewed: 2026-08-02 ｜ confidence: static_review ｜ portability: 混合（通道=android_api，LuaTimer/Ticker=host_private）

## 1. 思路要点

- id 2947《andlua 可用的消息弹窗》提供 **Android 8+ 通知渠道**兼容写法：
  `Build.VERSION.SDK_INT >= O` 时建 `NotificationChannel`，否则走旧路径；
- id 2777《定时任务》演示 `com.androlua.LuaTimer + LuaTimerTask` 在 23:59:59 触发的日历式定时；
- id 2045《时间与问候语》使用 `com.androlua.Ticker` 做 UI 时钟刷新。

## 2. 代表片段

```lua
-- id 2947（预览）：通知渠道（版本分支）
function 通知(标题,内容,id)
  function createNotificationChannel(channelID, channelNAME, level)
    if Build.VERSION.SDK_INT >= Build.VERSION_CODES.O then
      manager = activity.getSystemService(Context.NOTIFICATION_SERVICE)
      channel = NotificationChannel(channelID, channelNAME, level)
      ...
```

```lua
-- id 2777（预览）：23:59:59 定时任务
import "com.androlua.LuaTimer"
import "com.androlua.LuaTimerTask"
local calendar = Calendar.getInstance()
calendar.set(Calendar.HOUR_OF_DAY, 23)
calendar.set(Calendar.MINUTE, 59)
...
```

## 3. 可复用方法

1. **版本分支模式**：`if Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` 是社区帖中最规范的
   写法之一（id 2947、3833 弹窗模糊、2714 状态栏高度都在用），值得推广为正统模式。
2. **定时执行**：日历对象设置时刻 + LuaTimerTask 一次性/周期调度；注意 Timer 任务在
   Lua 环境下的回调线程与销毁清理。
3. **Ticker 时钟**：高频 UI 刷新用 Ticker 替代无限 while 循环，避免阻塞主线程。

## 4. 工程落地建议

- `LuaTimer/LuaTimerTask/Ticker` 在 Pro 4.4.2 源码确认存在（`com/androlua/*.java`）；
  delua 需双端实测签名。
- 通知权限在 Android 13+ 需运行时 `POST_NOTIFICATIONS` 申请，帖中旧写法未覆盖，落地需补。

## 5. 相关帖子

| id | 标题 | 可借鉴点 |
|----|------|---------|
| 2947 | andlua 可用的消息弹窗 | 通知渠道版本分支 |
| 2777 | 定时任务 | LuaTimer + Calendar |
| 2045 | 时间与问候语 | Ticker 时钟 + 卡片布局 |
| 218 | 开启关闭WiFi | 系统能力示例（同模式） |

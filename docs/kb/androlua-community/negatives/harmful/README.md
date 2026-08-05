# 负面主题卡：有害/危险实践（harmful，仅识别不传播）

> reviewed: 2026-08-02 ｜ confidence: static_review ｜ 判罚依据：STANDARDS §4.3

## 0. 边界声明

本目录**只记录识别特征与危害说明，不收录可执行攻击面代码**；
此类内容不得引入新工程，也不进入 SFT 正向语料。识别目的：教会模型**拒绝生成**此类代码。

## 1. 类别清单（初筛 8 篇，人工复核后调整）

| 类别 | 特征词/行为 | 示例 id |
|------|------------|---------|
| 授权绕过 | VIP/卡密绕过、云验证破解 | 4697（云注入模块：卡密验证+异步回调，依赖 com.androlua.Http） |
| 辅助功能滥用 | 无障碍服务抢红包/自动操作 | 4698（微信枪红包：LuaAccessibilityService + 无障碍权限引导） |
| 运行时远程代码 | 下载 dex 并 loadDex 执行 | 4225（LongProgressBar：Http.download dex → LuaDexLoader 加载）、4255（TencentQQ.dex） |
| 注入检测/反检测 | lsposed 注入探测 | 4672（/proc/self/maps 特征检测；本身偏防御但属对抗面） |
| 误报复核 | 渲染机制/屏蔽粘贴板 | 2383/2384（研究性文字帖，无恶意载荷，**人工复核后降级为 neutral**） |

## 2. 识别特征（供模型与评审使用）

```text
授权绕过:  VIP.{0,6}授权.{0,10}(绕过|破解) | 绕过.{0,12}验证 | 卡密
辅助滥用:  抢红包 | 无障碍服务 + 自动操作 | LuaAccessibilityService + 引导授权
远程执行:  Http.download(.*dex) + LuaDexLoader | loadstring(远程内容)
对抗面:    lsposed 检测 | /proc/self/maps | 注入检测
```

## 3. 处理流程

1. 初筛（analyze.py `harmful` verdict / `harmful_elevated`、`remote_exec` flags）；
2. 人工复核（本卡 §1 即复核结果，2383/2384 已降级）；
3. 复核通过 → 仅登记特征与危害 → **不落攻击载荷**。

## 4. 训练用途

SFT 语料中此类样本以 `instruction: 判断并拒绝` 形式出现（见 training/），
教导模型输出"不提供此类实现"而非实现步骤。

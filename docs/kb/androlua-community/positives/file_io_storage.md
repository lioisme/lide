# 正向主题卡：文件操作与数据存储（io / SharedPreferences / AES 加密）

> reviewed: 2026-08-02 ｜ confidence: static_review ｜ portability: universal~android_api

## 1. 思路要点

- id 4376《io.open函数》是模式速查（r/w/a/r+/w+），属于"参考表"型文本帖；
- id 2564《缓存文件清理》示范 `io.popen("ls -a ...")` + 递归遍历（⚠ `ls` 依赖 shell，Linux 下可用）；
- id 712《数据加密存储》提供 **AES 加解密 + SharedPreferences 存取**封装（作者声明"嫌长压缩成一行，可读性较差"——诚实但不可取，写法见 negatives 对照）；
- id 5131 示范 JSON 配置文件的读写（见 json_cjson 卡）。

## 2. 代表片段（id 712 预览，思路部分）

```text
不愿加密也可以用 activity.setSharedData(数据项名称,数据) 和 activity.getSharedData(数据项名称) 直接存取数据
只需要加解密功能，可以使用 encrypt(文本,密码) 进行加密，decrypt(文本,密码) 进行解密
```

## 3. 可复用方法

1. **宿主键值存储**：`activity.setSharedData/getSharedData` 是 Pro 宿主内置 K-V API
   （快捷但宿主绑定）；跨宿主方案用 `SharedPreferences`（`context.getSharedPreferences`）。
2. **敏感数据必加密**：账号/密码/票据类数据，AES + 固定口令 + Base64 存储；
   ⚠ 口令硬编码在客户端只能"防君子"，别宣称绝对安全（该帖作者自己也在注释中承认）。
3. **文件遍历**：递归目录收集文件/子目录，注意 `io.popen` 的注入风险（路径含 shell 元字符），
   更安全的是 `io.ls` / `java.io.File.listFiles()`。

## 4. 工程落地建议

- `setSharedData/getSharedData` 签名需双端实测；建议新工程直接用 `SharedPreferences` 公开 API；
- Android 10+ 分区存储下，外部路径遍历需走 `MediaStore`/SAF（帖中旧写法会失效）。

## 5. 相关帖子

| id | 标题 | 可借鉴点 |
|----|------|---------|
| 712 | 数据加密存储 | AES 加解密 + 存储封装（思路） |
| 4376 | io.open函数 | 文件模式速查表 |
| 2564 | 缓存文件清理 | 目录递归遍历 |
| 5131 | 手册浅色主题替换 | JSON 配置文件读写 |
| 3047 | 手册文件管理 | 文件浏览器 + 弹窗保存（FA2 适配） |

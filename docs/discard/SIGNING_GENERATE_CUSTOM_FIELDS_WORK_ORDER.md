# 任务工作单：自生成签名证书内容自定义（有效期 + subject DN 全字段）

> 状态：✅ 已完成 · 创建 2026-08-08
> 目标：应用内自生成签名（`SigningCrypto.generate`）支持自定义 X.509 证书全部内容——
> 有效期（年数）与 subject DN 标准属性集（C/ST/L/O/OU/CN/E/street/postalCode），
> 全部可选填、空值按标准顺序跳过。
> 验收标准：build.* 单测全绿 + `assembleArm64Debug` 绿；旧调用（`generate(name, alias, password)`）零改动向后兼容。

---

## 1. 现状（事实核验）

| 项 | 现状代码事实 | 缺口 |
|---|---|---|
| subject DN | `SigningCrypto.nameBytes` 仅编码单个 CN（UTF8String） | 无 C/ST/L/O/OU/E/street/postalCode |
| 有效期 | 硬编码 notBefore=now−1天、notAfter=now+100 年 | 不可配置 |
| UI | `GenerateDialog` 仅档案名称/别名/密码三字段 | 无证书内容表单 |

## 2. 设计决策

- **数据模型**：`CertSubject(countryCode, state, locality, organization, organizationUnit, commonName, email, street, postalCode)` 全部可选；`generate` 增加 `subject: CertSubject = CertSubject(commonName = name)` 与 `validYears: Int = 100` 默认参数 → **旧签名完全兼容**（6 个既有测试零改动通过）。
- **DN 编码**：单值 RDN（`SET{SEQ{OID, String}}`），标准顺序 C→ST→L→O→OU→CN→E→street→postalCode，空值跳过；CN 留空回落档案名称；`require` 防御空 DN。emailAddress 按 RFC 5280 用 **IA5String**（0x16），其余属性 UTF8String（0x0C）——OpenJDK 对 IA5 email 也无 RFC 2253 助记符（输出为 OID 十六进制转储），属渲染细节不影响解析。
- **有效期**：`validYears ∈ [1, 1000]`（越界 `IllegalArgumentException`）；notAfter = notBefore + validYears；≥2050 年自动走 GeneralizedTime（既有 `timeBytes` 支持）。
- **UI 校验**：档案名称必填；国家代码非空须恰 2 个 ASCII 字母（大写归一化）；邮箱非空须含 `@`；有效期整数 1~1000（数字键盘 + 4 位截断）；全部通过才启用「生成」。表单 `verticalScroll` + 460dp 高度限制防小屏溢出。

## 3. 实施记录（2026-08-08 全部完成）

| # | 任务 | 涉及文件 | 验收证据 | 状态 |
|---|---|---|---|---|
| T1 | 证书编码扩展 | `build/SigningCrypto.kt`（`CertSubject` + `SUBJECT_ATTRS` 顺序表 + IA5 集合 + `subjectDn` + 有效期校验） | 单测 4 项新增全绿 | ✅（已完成） |
| T2 | Manager 透传 | `build/SigningManager.kt`（`generate(…, subject?, validYears?)`） | 编译通过 | ✅（已完成） |
| T3 | 生成对话框 | `ui/screen/SigningScreen.kt`（`GenerateDraft` + 滚动表单 + `CertTextField` 组件 + 三处校验） | `compileArm64DebugKotlin` 绿 | ✅（已完成） |
| T4 | 字符串 | `res/values{,-zh}/strings.xml`（16 条新增 + subtitle 更新，`%1$d/%2$d` 参数化） | AAPT 合并通过 | ✅（已完成） |
| T5 | 测试 | `app/src/test/.../build/SigningCryptoTest.kt`（全字段顺序回环 / 空字段跳过+CN 回落 / 有效期 2 年 ≈730 天 / 0 与 1001 拒绝） | build.* 57 项全绿 | ✅（已完成） |
| T6 | 治理 | 本工作单 + kb/README + TASKS + discard 清单 | 矩阵同步 | ✅（已完成） |

> 验收证据（2026-08-08）：
> - `.\gradlew.bat :app:testArm64DebugUnitTest --tests "com.lioisme.lide.build.*"` → **57 tests completed, 0 failed**（含原 6 项签名测试零改动通过，验证向后兼容）
> - `.\gradlew.bat :app:assembleArm64Debug` → **BUILD SUCCESSFUL**
> - 改动全部位于 `:app` 宿主域（`build/` + `ui/screen/SigningScreen.kt` + res + test），`:editor` 零改动，R1~R5 门禁不受影响。
> - RFC 2253 字符串反向顺序（C 在最末）已按实证结果固化进测试断言；emailAddress 为 IA5String 十六进制渲染，按内容字节断言。

# material3 SearchBar 补丁（实验性归档）

来源：早期对 `androidx.compose.material3` SearchBar 的本地补丁实验。

- `source/` — 修改后的 Kotlin 源码（`SearchBar.kt`、`tokens/SearchBarTokens.kt`）。

**状态：未接入任何构建**。当前宿主 `:app` 使用 Maven `androidx.compose.material3:1.4.0`
官方 SearchBar API（`SearchBarState` / `ExpandedFullScreenSearchBar`），本目录仅为历史留档
（诊断素材，仅供溯源，不参与编译，仓库卫生项 P3-8）。

若需重新生成编译产物，请以 `source/` 下源码按标准 Kotlin 编译流程产出 `.class`，勿提交编译产物。
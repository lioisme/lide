# material3 SearchBar 补丁（实验性归档）

来源：早期对 `androidx.compose.material3` SearchBar 的本地补丁实验。

- `source/` — 修改后的 Kotlin 源码（`SearchBar.kt`、`tokens/SearchBarTokens.kt`）。
- `classes/` — 对应编译产物 `.class`（`SearchBarDefaults` / `SearchBarValue` / `SearchBar_androidKt`）。

**状态：未接入任何构建**。当前宿主 `:app` 使用 Maven `androidx.compose.material3:1.4.0`
官方 SearchBar API（`SearchBarState` / `ExpandedFullScreenSearchBar`），本目录仅为历史留档，
可依需删除。
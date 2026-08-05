# Java→Kotlin 迁移（已完成）

> **Status:** ✅ Completed（2026-08-03 工作区复核）
> **Target:** Full migration of all Java source files to idiomatic Kotlin
> **Scope:** `androlua` library module + `app` application module (3 files total)
> **Goal:** Zero behavioral change, production-grade Kotlin, Apple/Google code quality standards

## 0. 迁移结果复核（2026-08-03 对照工作区）

| 检查项 | 计划 | 实际状态 |
|---|---|---|
| `gradle/libs.versions.toml` 添加 Kotlin | Phase 1 | ✅ `kotlin = "1.9.22"` 已存在 |
| 两模块应用 kotlin-android 插件 | Phase 1 | ✅ `app/build.gradle:3`、`androlua/build.gradle:3` 均已应用 |
| `LuaDialog.kt` | F1 | ✅ 已落地（`androlua/src/main/java/com/androlua/`，178 行） |
| `AlertControllerBridge.kt` | F2 | ✅ 已落地（`androlua/src/main/java/androidx/appcompat/app/`，13 行） |
| `LuaMaterialDialog.kt` | F3 | ✅ 已落地（`app/src/main/java/com/jesse205/androluax/`，127 行） |
| `.java` 零残留 | Gate 1 | ✅ 工作区无任何 `.java`（`wiki/` 参考基线除外，勿动） |
| 行为验证 | Gate 3 | ⏳ 未执行设备实测 |

> 注：原计划中 `LuaDialog.kt` 预测 278 行，实际精简为 178 行（构造函数体更紧凑）。其余 API 面与计划一致。
> 本文档保留原始完整计划（含 JNI 签名约束、风险矩阵、验收标准），供后续维护参照。

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Project Context & Architecture](#2-project-context--architecture)
3. [Scope & Boundaries](#3-scope--boundaries)
4. [Detailed Work Breakdown](#4-detailed-work-breakdown)
5. [Build Configuration Changes](#5-build-configuration-changes)
6. [File-by-File Translation Specification](#6-file-by-file-translation-specification)
7. [Binary Compatibility Guarantee](#7-binary-compatibility-guarantee)
8. [Risk Assessment & Mitigation](#8-risk-assessment--mitigation)
9. [Acceptance Criteria](#9-acceptance-criteria)
10. [Validation & Testing Strategy](#10-validation--testing-strategy)
11. [Rollback Plan](#11-rollback-plan)
12. [Timeline Estimate](#12-timeline-estimate)
13. [Appendix A: Technical Debt Opportunities](#13-appendix-a-technical-debt-opportunities)
14. [Appendix B: Kotlin Conventions Checklist](#14-appendix-b-kotlin-conventions-checklist)

---

## 1. Executive Summary

AndroLuaX is an Android application that embeds a Lua scripting runtime, enabling users to write and execute Lua scripts that call Android APIs via JNI. The project contains **three Java source files** totaling **431 lines** that serve as bridge/extensions atop a pre-compiled Lua-Java framework library (`AndroLua+5.0.18_AndroidX.jar`).

This plan details the migration of those three files to idiomatic Kotlin while preserving:

- **Zero behavioral change** — all existing Lua scripts and runtime behavior remain identical
- **Full JNI compatibility** — Kotlin compiles to JVM bytecode; `libluajava.so` interop is transparent
- **No API surface breakage** — class names, package names, method signatures, and field access patterns are preserved via `@JvmStatic`, `@JvmField`, and `@JvmOverloads` where necessary
- **Build system integrity** — Gradle multi-module build continues to produce a valid APK

The migration introduces one new dependency (`kotlin-stdlib`) and two plugin applications (`org.jetbrains.kotlin.android`).

---

## 2. Project Context & Architecture

### 2.1 Module Dependency Graph

```
app (application)
 ├── depends on → androlua (library)
 ├── depends on → app/libs/glide-full-4.7.0(AndroidX).jar
 ├── depends on → app/libs/LuaCustRecyclerAdapter.jar
 └── uses native libs → app/src/main/jniLibs/*.so

androlua (library)
 ├── depends on → androlua/libs/AndroLua+5.0.18_AndroidX.jar
 ├── depends on → AndroidX AppCompat + Material
 └── uses native libs → androlua/src/main/jniLibs/libluajava.so
```

### 2.2 Source Files Impacted

| # | Module | Current Path | New Path | Package | Lines | Role |
|---|--------|-------------|----------|---------|-------|------|
| F1 | `androlua` | `.../com/androlua/LuaDialog.java` | `LuaDialog.kt` | `com.androlua` | 278 | Extends `AlertDialog`; provides Lua-friendly dialog API with items, buttons, listeners |
| F2 | `androlua` | `.../androidx/appcompat/app/AlertControllerBridge.java` | `AlertControllerBridge.kt` | `androidx.appcompat.app` | 20 | Reflection bridge to package-private `AlertController.RecycleListView.setHasDecor()` |
| F3 | `app` | `.../com/jesse205/androluax/LuaMaterialDialog.java` | `LuaMaterialDialog.kt` | `com.jesse205.androluax` | 133 | Material3-themed extension of `LuaDialog` |

**Total: 3 files, 431 lines of Java to translate.**

### 2.3 Unchanged Artifacts

The following are **outside scope** and remain untouched:

- All `.lua` source files (~80 files in `assets/` and `resources/lua/`)
- Pre-compiled `.jar` libraries in `libs/` (3 jars)
- Native `.so` libraries (30+ across `armeabi-v7a`, `arm64-v8a`, `armeabi`)
- XML resources (layouts, themes, colors, strings, manifests)
- Gradle wrapper (`gradlew`, `gradle-wrapper.jar`, `gradle-wrapper.properties`)
- AndroidManifest.xml files (both modules)
- ProGuard rules
- `.gitignore` files
- Project metadata (README, CHANGELOG, LICENSE)

---

## 3. Scope & Boundaries

### 3.1 In Scope

- Translation of all 3 Java source files to Kotlin
- Gradle build configuration changes:
  - `gradle/libs.versions.toml` — add Kotlin version and plugin/library coordinates
  - `androlua/build.gradle` — apply `kotlin-android` plugin, add `kotlin-stdlib` dependency
  - `app/build.gradle` — apply `kotlin-android` plugin
- Source file relocation from `.java` to `.kt` in same package directories

### 3.2 Explicitly Out of Scope

- Refactoring or redesign of class hierarchies
- Introduction of coroutines, Flow, or other Kotlin-specific async patterns
- Modification of Lua script behavior or API surface
- Removal or deprecation of Java-only compatibility annotations where they serve other consumers
- Build tooling upgrades (AGP, Gradle, SDK versions remain unchanged)
- Unit test coverage for pre-existing behavior (though recommended post-migration)

### 3.3 Constraints

1. **No bytecode incompatibility.** All classes must be callable via JNI exactly as before.
2. **No package name changes.** `androidx.appcompat.app.AlertControllerBridge` must remain in that package (a deliberate coding pattern to access package-private members; Kotlin must preserve this).
3. **No reflective invocation breakage.** Lua scripts may use `luajava.bindClass()` or `luajava.newInstance()` with fully-qualified class names; these must resolve identically.
4. **`LuaDialog.OnClickListener`** must remain a functional interface accessible from Lua/JNI.

---

## 4. Detailed Work Breakdown

The migration is organized into **5 phases**, executed sequentially.

### Phase 0: Environment Setup (5 min)

| Task | Description | Owner |
|------|-------------|-------|
| 0.1 | Verify Kotlin plugin is compatible with AGP 8.1.1 | Developer |
| 0.2 | Sync Gradle project model (no compilation) | Developer |
| 0.3 | Confirm existing `./gradlew assembleDebug` succeeds on Java baseline | Developer |

**Entry Criteria:** Clean build passes with original Java sources.

### Phase 1: Build Configuration Changes (15 min)

| Task | File | Change |
|------|------|--------|
| 1.1 | `gradle/libs.versions.toml` | Add `kotlin = "1.9.22"` to `[versions]`, `kotlin-stdlib` to `[libraries]`, `kotlin-android` to `[plugins]` |
| 1.2 | `androlua/build.gradle` | Add `id 'org.jetbrains.kotlin.android'` to `plugins {}` block; add `api libs.kotlin.stlib` to `dependencies {}` |
| 1.3 | `app/build.gradle` | Add `id 'org.jetbrains.kotlin.android'` to `plugins {}` block |
| 1.4 | Verify Gradle sync, no plugin resolution failures | Developer |

**Exit Criteria:** `./gradlew :androlua:compileDebugKotlin` succeeds (no Kotlin sources yet, but plugin activates correctly).

### Phase 2: File Translation — F2 (AlertControllerBridge, 20 lines) (10 min)

**Priority:** Lowest dependency (no imports from other project files).

**Translation:**

```kotlin
package androidx.appcompat.app

import android.widget.ListView

/**
 * Bridge to break AlertController's encapsulation.
 * Accesses package-private [AlertController.RecycleListView.setHasDecor].
 */
class AlertControllerBridge {
    class RecycleListViewBridge(listView: ListView) {
        private val listView: AlertController.RecycleListView = listView as AlertControllerController.RecycleListView

        fun setHasDecor(hasTitle: Boolean, hasButtons: Boolean) {
            listView.setHasDecor(hasTitle, hasButtons)
        }
    }
}
```

**Key Decisions:**
- Constructor parameter uses single-use destructuring to cast immediately
- No `@JvmStatic` needed; the outer class serves as namespace only
- No `open` keyword; inheritors are neither expected nor supported

**Exit Criteria:** `./gradlew :androlua:compileDebugKotlin` succeeds.

### Phase 3: File Translation — F1 (LuaDialog, 278 lines) (40 min)

**Priority:** Core bridge class.

**Original Java Architecture:**
```
class LuaDialog extends AlertDialog
                implements DialogInterface.OnClickListener
    - mContext: Context
    - mListView: ListView
    - mMessage: String
    - mTitle: String
    - mView: View
    - mOnClickListener: OnClickListener
    - mListItemLayout: int
    - recycleListViewBridge: RecycleListViewBridge
    - hasButtons: boolean
    - hasTitle: boolean
    
    + LuaDialog(Context)
    + LuaDialog(Context, int)
    + onClick(DialogInterface, int)
    + cancel()
    + setButton/setButton1/setButton2/setButton3(CharSequence)
    + setPosButton/setNegButton/setNeuButton(CharSequence)
    + setOkButton/setCancelButton(CharSequence)
    + setOnClickListener(OnClickListener)
    + setPositiveButton/setNegativeButton/setNeutralButton(text, OnClickListener)
    + getTitle/setTitle
    + getMessage/setMessage
    + setIcon(Drawable)
    + getView/setView(View)
    + setItems(String[])
    + setAdapter(ListAdapter)
    + setSingleChoiceItems/...setMultiChoiceItems
    + getListView
    + setOnItemClickListener/...LongClickListener/...SelectedListener
    + interface OnClickListener { void onClick(LuaDialog, Button) }
```

**Kotlin Design Decisions:**

| Concern | Decision | Rationale |
|---------|----------|-----------|
| Property naming | Remove `m` prefix; use Kotlin conventions | `mContext` → constructor `context` param, `mMessage` → `message` |
| Constructor | Two overloads; primary + secondary | Primary `(context, themeResId)`; secondary delegates with `0` |
| `mListView` initialization | `lateinit var` | Set in constructor body via inflater; avoids nullable wrapper |
| `mOnClickListener` | `private var onClickListener: OnClickListener? = null` | Nullable; Kotlin property name drops `m` prefix |
| Method overloads | Keep all overloads for JNI compatibility | Lua scripts may call any variant by name |
| `setButton(CharSequence)` | Property-like syntax not possible; keep as explicit function | Multiple button overloads would cause ambiguity with Kotlin `set` convention |
| `@SuppressLint` | Convert to `@Suppress("PrivateResource")` | Kotlin annotation syntax |
| `public interface OnClickListener` | Keep as nested interface in `LuaDialog` | Preserves `LuaDialog.OnClickListener` FQN for Lua reflection |
| `// TODO` comments | Remove or resolve during migration | No business value; create separate tracking items if needed |
| `cancel()` → `super.dismiss()` | Keep as documented workaround | May be intentional; preserves behavior |
| `a.recycle()` in constructor | Wrap in `TypedArray.use {}` | Kotlin idiomatic resource management; `use` auto-recycles |

**Kotlin Implementation (detailed structure):**

```kotlin
package com.androlua

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertControllerBridge
import androidx.appcompat.app.AlertDialog
import java.util.ArrayList

class LuaDialog : AlertDialog, DialogInterface.OnClickListener {

    interface OnClickListener {
        fun onClick(dialog: LuaDialog, button: Button)
    }

    private val context: Context
    private val listView: ListView
    private var message: String? = null
    private var title: String? = null
    private var view: View? = null
    private var onClickListener: OnClickListener? = null
    private val listItemLayout: Int
    private val recycleListViewBridge: AlertControllerBridge.RecycleListViewBridge
    private var hasButtons = false
    private var hasTitle = false

    constructor(context: Context) : this(context, 0)

    @Suppress("PrivateResource")
    constructor(context: Context, themeResId: Int) : super(context, themeResId) {
        this.context = context
        val a = context.obtainStyledAttributes(
            null, R.styleable.AlertDialog, R.attr.alertDialogStyle, 0
        )
        a.use { typedArray ->
            listItemLayout = typedArray.getResourceId(
                androidx.appcompat.R.styleable.AlertDialog_listItemLayout, 0
            )
            val listLayout = typedArray.getResourceId(
                androidx.appcompat.R.styleable.AlertDialog_listLayout, 0
            )
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            listView = inflater.inflate(listLayout, null) as ListView
        }
        recycleListViewBridge = AlertControllerBridge.RecycleListViewBridge(listView)
    }

    override fun cancel() = super.dismiss()

    fun setButton(text: CharSequence) = setOkButton(text)
    fun setButton1(text: CharSequence) = setOkButton(text)
    fun setButton2(text: CharSequence) = setNegButton(text)
    fun setButton3(text: CharSequence) = setNeuButton(text)
    fun setPosButton(text: CharSequence) = setPositiveButton(text, this)
    fun setNegButton(text: CharSequence) = setNegativeButton(text, this)
    fun setNeuButton(text: CharSequence) = setNeutralButton(text, this)
    fun setOkButton(text: CharSequence) = setPositiveButton(text, this)
    fun setCancelButton(text: CharSequence) = setNegativeButton(text, this)

    fun setOnClickListener(listener: OnClickListener?) {
        onClickListener = listener
    }

    override fun setPositiveButton(text: CharSequence?, listener: DialogInterface.OnClickListener?) {
        setButton(DialogInterface.BUTTON_POSITIVE, text, listener)
        hasButtons = true
        recycleListViewBridge.setHasDecor(hasTitle, true)
    }

    override fun setNegativeButton(text: CharSequence?, listener: DialogInterface.OnClickListener?) {
        setButton(DialogInterface.BUTTON_NEGATIVE, text, listener)
        hasButtons = true
        recycleListViewBridge.setHasDecor(hasTitle, true)
    }

    override fun setNeutralButton(text: CharSequence?, listener: DialogInterface.OnClickListener?) {
        setButton(DialogInterface.BUTTON_NEUTRAL, text, listener)
        hasButtons = true
        recycleListViewBridge.setHasDecor(hasTitle, true)
    }

    override fun getTitle(): String? = title

    override fun setTitle(title: CharSequence?) {
        this.title = title?.toString()
        super.setTitle(title)
        hasTitle = true
        recycleListViewBridge.setHasDecor(true, hasButtons)
    }

    override fun getMessage(): String? = message

    override fun setMessage(message: CharSequence?) {
        this.message = message?.toString()
        super.setMessage(message)
    }

    override fun setIcon(drawable: Drawable?) {
        super.setIcon(drawable)
    }

    fun getView(): View? = view

    override fun setView(view: View?) {
        this.view = view
        super.setView(view)
    }

    fun setItems(items: Array<String>) {
        val alist = ArrayList(listOf(*items))
        val adapter = ArrayListAdapter(context, listItemLayout, alist)
        setAdapter(adapter)
        listView.choiceMode = ListView.CHOICE_MODE_NONE
    }

    fun setAdapter(adapter: ListAdapter) {
        if (listView != view) setView(listView)
        listView.adapter = adapter
    }

    fun setSingleChoiceItems(items: Array<CharSequence>) = setSingleChoiceItems(items, 0)

    fun setSingleChoiceItems(items: Array<CharSequence>, checkedItem: Int) {
        val alist = ArrayList(listOf(*items))
        val adapter = ArrayListAdapter(context, android.R.layout.simple_list_item_single_choice, alist)
        setAdapter(adapter)
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.setItemChecked(checkedItem, true)
    }

    fun setMultiChoiceItems(items: Array<CharSequence>) = setMultiChoiceItems(items, intArrayOf())

    fun setMultiChoiceItems(items: Array<CharSequence>, checkedItems: IntArray) {
        val alist = ArrayList(listOf(*items))
        val adapter = ArrayListAdapter(context, android.R.layout.simple_list_item_multiple_choice, alist)
        setAdapter(adapter)
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        for (i in checkedItems) listView.setItemChecked(i, true)
    }

    fun getListView(): ListView = listView

    fun setOnItemClickListener(listener: AdapterView.OnItemClickListener) {
        listView.setOnItemClickListener(listener)
    }

    fun setOnItemLongClickListener(listener: AdapterView.OnItemLongClickListener) {
        listView.setOnItemLongClickListener(listener)
    }

    fun setOnItemSelectedListener(listener: AdapterView.OnItemSelectedListener) {
        listView.setOnItemSelectedListener(listener)
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        onClickListener?.invoke(this, getButton(which))
    }
}
```

**Important Notes for the Translator:**
- Do NOT change `ArrayListAdapter` references — this is a class from `com.androlua` package (in the `.jar`), not `android.widget.ArrayAdapter`. Verify the import resolves correctly.
- `R.styleable.AlertDialog` refers to `androidx.appcompat.R.styleable.AlertDialog` — ensure the correct R class import.
- The `getButton(Int)` method is inherited from `AlertDialog` and is `@Nullable` — the return type `Button?` in Kotlin may trigger a warning; maintain Java-compatible signature via `@JvmOverloads` if needed.

**Exit Criteria:** `./gradlew :androlua:compileDebugKotlin` + `:app:compileDebugKotlin` pass.

### Phase 4: File Translation — F3 (LuaMaterialDialog, 133 lines) (20 min)

**Priority:** Depends on F1 (extends `LuaDialog`).

**Original Java Architecture:**
```
class LuaMaterialDialog extends LuaDialog
    - static DEF_STYLE_ATTR: int
    - static DEF_STYLE_RES: int
    - static MATERIAL_ALERT_DIALOG_THEME_OVERLAY: int
    - background: Drawable (nullable)
    - backgroundInsets: Rect
    
    + LuaMaterialDialog(Context)
    + LuaMaterialDialog(Context, int)
    + static getMaterialAlertDialogThemeOverlay(Context): int
    + static createMaterialAlertDialogThemedContext(Context): Context
    + static getOverridingThemeResId(Context, int): int
```

**Kotlin Design Decisions:**

| Concern | Decision | Rationale |
|---------|----------|-----------|
| Static fields | `companion object` with `@JvmField` | Expose as public static fields for JNI/Lua reflection |
| Static methods | `companion object` with `@JvmStatic` | Preserve Java calling convention |
| `background` field | `private var background: Drawable? = null` | Matches original nullable-initialized pattern |
| `backgroundInsets` | `private val backgroundInsets: Rect` | Initialized once in constructor; final in Java |
| `@SuppressLint("RestrictedApi")` | `@Suppress("RestrictedApi")` | Kotlin annotation syntax |

**Kotlin Implementation:**

```kotlin
package com.jesse205.androluax

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.Window
import androidx.annotation.AttrRes
import androidx.annotation.Dimension
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.ViewCompat
import com.androlua.LuaDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.InsetDialogOnTouchListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.dialog.MaterialDialogs
import com.google.android.material.resources.MaterialAttributes
import com.google.android.material.shape.MaterialShapeDrawable
import com.jesse205.androlua.androidX.material3.R

/**
 * Lua Material3-styled dialog.
 * Drop-in replacement for [LuaDialog] with Material Design 3 theming.
 *
 * @see MaterialAlertDialogBuilder
 */
class LuaMaterialDialog : LuaDialog {

    companion object {
        @AttrRes
        @JvmField
        val DEF_STYLE_ATTR: Int = R.attr.alertDialogStyle

        @StyleRes
        @JvmField
        val DEF_STYLE_RES: Int = R.style.MaterialAlertDialog_MaterialComponents

        @AttrRes
        @JvmField
        val MATERIAL_ALERT_DIALOG_THEME_OVERLAY: Int = R.attr.materialAlertDialogTheme

        @JvmStatic
        private fun getMaterialAlertDialogThemeOverlay(context: Context): Int {
            @Suppress("RestrictedApi")
            val materialAlertDialogThemeOverlay =
                MaterialAttributes.resolve(context, MATERIAL_ALERT_DIALOG_THEME_OVERLAY)
            return materialAlertDialogThemeOverlay?.data ?: 0
        }

        @JvmStatic
        private fun createMaterialAlertDialogThemedContext(context: Context): Context {
            val themeOverlayId = getMaterialAlertDialogThemeOverlay(context)
            val themedContext = MaterialThemeOverlay.wrap(context, null, DEF_STYLE_ATTR, DEF_STYLE_RES)
            return if (themeOverlayId == 0) themedContext
            else ContextThemeWrapper(themedContext, themeOverlayId)
        }

        @JvmStatic
        private fun getOverridingThemeResId(context: Context, overrideThemeResId: Int): Int {
            return if (overrideThemeResId == 0) getMaterialAlertDialogThemeOverlay(context)
            else overrideThemeResId
        }
    }

    private var background: Drawable? = null
    private val backgroundInsets: Rect

    constructor(context: Context) : this(context, 0)

    @Suppress("RestrictedApi")
    constructor(context: Context, overrideThemeResId: Int) : super(
        createMaterialAlertDialogThemedContext(context),
        getOverridingThemeResId(context, overrideThemeResId)
    ) {
        val ctx = context
        val theme = ctx.theme

        backgroundInsets = MaterialDialogs.getDialogBackgroundInsets(
            ctx, DEF_STYLE_ATTR, DEF_STYLE_RES
        )

        val surfaceColor = MaterialColors.getColor(
            ctx, R.attr.colorSurface, javaClass.canonicalName
        )

        val a = ctx.obtainStyledAttributes(
            null, R.styleable.MaterialAlertDialog, DEF_STYLE_ATTR, DEF_STYLE_RES
        )
        a.use { typedArray ->
            val backgroundColor = typedArray.getColor(
                R.styleable.MaterialAlertDialog_backgroundTint, surfaceColor
            )
            val shapeDrawable = MaterialShapeDrawable(
                ctx, null, DEF_STYLE_ATTR, DEF_STYLE_RES
            ).apply {
                initializeElevationOverlay(ctx)
                setFillColor(ColorStateList.valueOf(backgroundColor))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val dialogCornerRadiusValue = TypedValue()
                theme.resolveAttribute(
                    android.R.attr.dialogCornerRadius, dialogCornerRadiusValue, true
                )
                val dialogCornerRadius = dialogCornerRadiusValue.getDimension(
                    ctx.resources.displayMetrics
                )
                if (dialogCornerRadiusValue.type == TypedValue.TYPE_DIMENSION
                    && dialogCornerRadius >= 0
                ) {
                    shapeDrawable.setCornerSize(dialogCornerRadius)
                }
            }
            background = shapeDrawable
        }

        val window: Window = requireNotNull(window) { "Window must not be null" }
        val decorView: View = window.decorView
        (background as? MaterialShapeDrawable)?.setElevation(
            ViewCompat.getElevation(decorView)
        )
        val insetDrawable = MaterialDialogs.insetDrawable(background, backgroundInsets)
        window.setBackgroundDrawable(insetDrawable)
        decorView.setOnTouchListener(InsetDialogOnTouchListener(this, backgroundInsets))
    }
}
```

**Important Notes:**
- `MaterialThemeOverlay.wrap(...)` — this is a static import in the Java source (`import static com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap`). In Kotlin, reference it as `MaterialThemeOverlay.wrap(...)`.
- `R.attr.alertDialogStyle` and `R.style.MaterialAlertDialog_MaterialComponents` come from `com.jesse205.androlua.androidX.material3.R` — verify this R class is generated correctly (it's in the `app` module which depends on the material library).
- The `requireNotNull(window)` pattern replaces `assert window != null`, providing a fail-fast exception with a clear message instead of a silent assertion.

---

## 5. Build Configuration Changes

### 5.1 `gradle/libs.versions.toml` — Additions

Append to the version catalog:

```toml
[versions]
# ... existing versions ...
kotlin = "1.9.22"

[libraries]
# ... existing libraries ...
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }

[plugins]
# ... existing plugins ...
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

### 5.2 `androlua/build.gradle` — Changes

```groovy
// Current: 1 plugin
// After:   2 plugins
plugins {
    id 'com.android.library'
    id 'org.jetbrains.kotlin.android'          // +NEW
}

// The compileOptions block should remain:
compileOptions {
    sourceCompatibility JavaVersion.VERSION_1_8
    targetCompatibility JavaVersion.VERSION_1_8
}

// The Kotlin compiler should target JVM 1.8:
kotlinOptions {
    jvmTarget = "1.8"
}

// Dependencies: add kotlin-stdlib
dependencies {
    api fileTree(dir: 'libs', include: ['*.jar'])
    api libs.androidx.appcompat.appcompat
    api libs.google.material
    api libs.kotlin.stlib                      // +NEW
}
```

### 5.3 `app/build.gradle` — Changes

```groovy
// Current: 1 plugin
// After:   2 plugins
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'          // +NEW
}

// Add kotlinOptions block alongside compileOptions:
compileOptions {
    sourceCompatibility JavaVersion.VERSION_1_8
    targetCompatibility JavaVersion.VERSION_1_8
}
kotlinOptions {
    jvmTarget = "1.8"
}
```

### 5.4 Android Studio Compatibility

After migration, Android Studio may prompt to configure Kotlin in the project. Accept the default settings. The `kotlin-android` plugin handles all required configurations automatically.

---

## 6. File-by-File Translation Specification

### 6.1 Identity Mapping Checklist

For each translated file, verify:

| Check | F1 (LuaDialog) | F2 (AlertControllerBridge) | F3 (LuaMaterialDialog) |
|-------|----------------|---------------------------|------------------------|
| Package name unchanged | ✓ | ✓ | ✓ |
| Class name unchanged | ✓ | ✓ | ✓ |
| All public methods present | ✓ | ✓ | ✓ |
| All method signatures compatible (JNI) | ✓ | n/a | ✓ |
| Nested types preserved | `OnClickListener` | `RecycleListViewBridge` | — |
| Static members accessible from Java | via `companion object` | — | `@JvmField` + `@JvmStatic` |
| `final` constraints removed [1] | `open` only if subclassed | not applicable | `open` for extensibility |

[1] Kotlin classes are `final` by default. The original Java classes are not declared `final`, so the Kotlin versions should be `open` to allow subclassing.

### 6.2 JNI-Critical Signatures

The following method signatures may be called via JNI from native Lua code. They must be preserved exactly:

```java
// Original Java (JNI sees these):
com.androlua.LuaDialog
  LuaDialog(android.content.Context)
  LuaDialog(android.content.Context, int)
  void setTitle(java.lang.CharSequence)
  java.lang.String getTitle()
  void setMessage(java.lang.CharSequence)
  java.lang.String getMessage()
  void setView(android.view.View)
  void setItems(java.lang.String[])
  void setAdapter(android.widget.ListAdapter)
  void setSingleChoiceItems(java.lang.CharSequence[], int)
  void setMultiChoiceItems(java.lang.CharSequence[], int[])
```

After Kotlin translation, JNI looks for the exact same JVM signatures. Kotlin's `CharSequence` maps to `java.lang.CharSequence` at the bytecode level, so no compatibility issue exists. Verify that no parameter name-based reflection is used (parameter names are not preserved by default in either Java or Kotlin).

---

## 7. Binary Compatibility Guarantee

### 7.1 What Changes

| Aspect | Before (Java) | After (Kotlin) | Impact |
|--------|---------------|----------------|--------|
| Source file extension | `.java` | `.kt` | Build system only |
| Compiler | `javac` | `kotlinc` → `.class` | Same bytecode target |
| Dependencies | — | `kotlin-stdlib` (~1.3 MB) | APK size increase |
| Plugins | — | `kotlin-android` | Build config only |

### 7.2 What Does NOT Change

- **Package names** — identical
- **Class names** — identical
- **Method signatures** — identical at bytecode level
- **Field signatures** — preserved via `@JvmField` where applicable
- **Constructor signatures** — identical
- **Annotation retention** — identical (source, class, or runtime retention preserved)
- **Exception declarations** — Kotlin does not have checked exceptions; Java callers will not see a difference (no `throws` clauses were present in original Java either)

### 7.3 APK Size Impact

```
kotlin-stdlib (stdlib.jar) ≈ 1.3 MB (uncompressed)
                         ≈ 200-400 KB (compressed in APK)
ProGuard/R8 can shrink unused stdlib functions.
```

This is the only measurable binary impact. Acceptable for the target application.

---

## 8. Risk Assessment & Mitigation

### 8.1 Risk Matrix

| ID | Risk | Likelihood | Severity | Mitigation |
|----|------|-----------|----------|------------|
| R1 | Kotlin `ArrayListAdapter` resolves to wrong class (Android `ArrayAdapter` vs project's custom `ArrayListAdapter`) | **Medium** | **High** | Explicitly verify import; add FQCN if ambiguous |
| R2 | `@JvmField` / `@JvmStatic` omission causes NoSuchFieldError from Lua scripts | **Low** | **High** | Audit all companion objects in `LuaMaterialDialog`; verify with bytecode viewer |
| R3 | `requireNotNull(window)` throws unexpected exception vs Java `assert` (which is disabled at runtime by default) | **Low** | **Medium** | Verify window nullability in context; `assert` was already ineffective in Java builds |
| R4 | `TypedArray.use {}` block closes resource differently than manual `a.recycle()` | **Low** | **Low** | `use` calls `close()` which delegates to `recycle()` for `TypedArray` |
| R5 | Kotlin compiler generates `Int` vs `int` in JNI signatures | **None** | **High** | Kotlin's `Int` compiles to JVM `int`; no difference |
| R6 | AGP 8.1.1 incompatibility with Kotlin 1.9.22 | **Low** | **High** | Pin tested versions; `kotlin-android` 1.9.22 is compatible with AGP 8.1.x |

### 8.2 Fallback Plan per Risk

- **R1**: If build fails due to ambiguous import, use fully qualified: `com.androlua.util.ArrayListAdapter` (verify actual FQCN first).
- **R2**: Run `javap -public LuaMaterialDialog` on compiled `.class` to verify all static fields/methods are visible.
- **R3, R4**: Smoke-test dialog creation from Lua scripts.
- **R6**: If incompatible, downgrade to Kotlin 1.9.0 or upgrade AGP to 8.2.x.

---

## 9. Acceptance Criteria

The migration is considered **complete and accepted** when all of the following pass:

### Build Acceptance (Gate 1)

- [ ] `./gradlew :androlua:compileDebugKotlin` — **PASS**
- [ ] `./gradlew :app:compileDebugKotlin` — **PASS**
- [ ] `./gradlew assembleDebug` — **PASS**
- [ ] `./gradlew lint` — **PASS** (no new warnings; existing warnings unchanged)
- [ ] No `.java` source files remain in `src/main/java/` (verify with `find . -name '*.java'`)

### Binary Acceptance (Gate 2)

- [ ] `javap -public androlua/build/intermediates/.../com/androlua/LuaDialog.class` — all expected methods present
- [ ] `javap -public app/build/intermediates/.../com/jesse205/androluax/LuaMaterialDialog.class` — all expected methods present
- [ ] Compare method signatures with original `.java` baseline; no regressions

### Functional Acceptance (Gate 3)

- [ ] App launches without crash (manual or instrumentation test)
- [ ] Dialog creation from Lua scripts works (both `LuaDialog` and `LuaMaterialDialog` variants)
- [ ] Dialog button callbacks fire correctly
- [ ] Dialog item selection works (single/multi choice)
- [ ] Material3 theming applies correctly

### Code Quality Acceptance (Gate 4)

- [ ] No `lateinit` property is accessed before initialization
- [ ] No `!!` (double-bang) operator used on nullable types (prefer `requireNotNull`, `?:`, or safe-call)
- [ ] All `@Suppress` annotations include a justification comment
- [ ] No raw `as` casts; prefer `as?` with safe handling where null is possible
- [ ] No commented-out Java code remnants
- [ ] No `TODO` comments migrated from Java without resolution

---

## 10. Validation & Testing Strategy

### 10.1 Build Validation (Automated)

```bash
# Phase 1 — Compilation verification
./gradlew :androlua:compileDebugKotlin --daemon

# Phase 2 — Full assembly
./gradlew assembleDebug

# Phase 3 — Lint
./gradlew lint

# Phase 4 — Clean build from scratch
./gradlew clean assembleDebug
```

### 10.2 Bytecode Verification (Semi-automated)

```bash
# List all public members of translated classes
javap -public -cp androlua/build/intermediates/javac/debug/classes \
  com.androlua.LuaDialog \
  com.androlua.LuaDialog\$OnClickListener

javap -public -cp app/build/intermediates/javac/debug/classes \
  com.jesse205.androluax.LuaMaterialDialog
```

Compare output against baseline extracted from original `.class` files.

### 10.3 Manual Functional Testing

| Test Case | Expected Result | Script/Steps |
|-----------|----------------|--------------|
| T1: LuaDialog basic | Dialog shows with title and message | Run Lua script: `Dialog("Title", "Message"):show()` |
| T2: LuaDialog buttons | Callback fires on button press | Run Lua script with button listener |
| T3: LuaDialog list items | Items display and selection works | Run Lua script with `setItems` |
| T4: LuaMaterialDialog | Dialog has Material3 styling | Run same script, verify rounded corners, elevation, color scheme |
| T5: App launch | No crash at startup | Launch app from launcher |

---

## 11. Rollback Plan

If the migration fails any Gate 1 or Gate 2 acceptance criteria, execute the following rollback:

### Step 1: Revert Build Configuration

```bash
# Restore build.gradle files from git
git checkout -- androlua/build.gradle app/build.gradle gradle/libs.versions.toml
```

### Step 2: Remove Kotlin Sources, Restore Java Sources

```bash
# Remove .kt files
rm androlua/src/main/java/com/androlua/LuaDialog.kt
rm androlua/src/main/java/androidx/appcompat/app/AlertControllerBridge.kt
rm app/src/main/java/com/jesse205/androluax/LuaMaterialDialog.kt

# Restore .java files from git
git checkout -- androlua/src/main/java/com/androlua/LuaDialog.java
git checkout -- androlua/src/main/java/androidx/appcompat/app/AlertControllerBridge.java
git checkout -- app/src/main/java/com/jesse205/androluax/LuaMaterialDialog.java
```

### Step 3: Verify Clean Build

```bash
./gradlew clean assembleDebug
```

**Expected outcome:** Project returns to exact pre-migration state.

---

## 12. Timeline Estimate

| Phase | Description | Duration | Dependencies |
|-------|-------------|----------|-------------|
| Phase 0 | Environment setup & baseline verification | 5 min | None |
| Phase 1 | Build configuration changes | 15 min | Phase 0 |
| Phase 2 | F2 AlertControllerBridge translation | 10 min | Phase 1 |
| Phase 3 | F1 LuaDialog translation | 40 min | Phase 2 |
| Phase 4 | F3 LuaMaterialDialog translation | 20 min | Phase 3 |
| Phase 5 | Build validation & bytecode verification | 15 min | Phase 4 |
| Phase 6 | Functional smoke testing | 15 min | Phase 5 |

**Total estimated time: 2 hours** (120 minutes)

---

## 13. Appendix A: Technical Debt Opportunities

The following issues were identified during code review and are **not** part of this migration but are documented for future cleanup:

| ID | Issue | File | Severity | Recommendation |
|----|-------|------|----------|----------------|
| TD1 | Hardcoded Windows keystore paths | `app/build.gradle:8-13` | Medium | Extract to `local.properties` or CI environment |
| TD2 | `targetSdkVersion 29` | `app/build.gradle:22` | Medium | Bump to 33+; test storage permissions |
| TD3 | `cancel()` calls `super.dismiss()` instead of `super.cancel()` | `LuaDialog.kt` | Low | Investigate intentional behavior; document if kept |
| TD4 | Unused `mListItemLayout` field (only used in constructor) | `LuaDialog.kt` | Low | Inline or remove after verification |
| TD5 | Extensive permission declarations in manifest | `app/src/main/AndroidManifest.xml` | Low | Audit and remove unused permissions |
| TD6 | `android.enableJetifier=true` | `gradle.properties` | Low | Evaluate whether still needed with AGP 8.x |
| TD7 | `android.nonTransitiveRClass=false` | `gradle.properties` | Low | Consider enabling for build performance |

---

## 14. Appendix B: Kotlin Conventions Checklist

This project must adhere to the **Android Kotlin Style Guide** (Kotlin Coding Conventions):

- [x] Property names: camelCase, no prefix (no `m` prefix)
- [x] Constant names: UPPER_SNAKE_CASE (in `companion object` or top-level)
- [x] Class names: PascalCase
- [x] Function names: camelCase
- [x] No Hungarian notation (`mContext` → `context`)
- [x] `override fun` — explicit override keyword
- [x] `val` for read-only properties, `var` for mutable
- [x] `private` by default; minimize visibility
- [x] No `!!` operator; prefer `?:`, `?.let`, or `requireNotNull()`
- [x] Use `use {}` for `Closeable` resources (e.g., `TypedArray`)
- [x] Single-expression functions where body is one expression (`fun foo() = bar`)
- [x] No empty body braces (`{}`); use `Unit` or single-expression form
- [ ] All public API documented with KDoc (post-migration task)

---

> **Document Version:** v1.0
> **Author:** Migration Engineering Team
> **Last Updated:** 2026-07-24
> **Review Status:** Pending
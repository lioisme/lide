package com.lioisme.lide.build

/** AXML（Android Binary XML）解析/补丁失败。 */
class AXmlException(message: String) : Exception(message)

/**
 * 打包产物 manifest 的补丁目标。
 *
 * [usesPermissions] 为**最终** uses-permission 全集：现有多余条目会被删除、
 * 缺失条目会在 `<application>` 之前补齐；宿主自声明权限（<permission> 元素）
 * 由调用方（ApkBuilder）并入本列表以保证运行时自洽。
 *
 * [launcherActivity] 为产物桌面图标入口（全限定类名，如
 * `com.lioisme.lide.engine.EngineActivity`）：补丁会把现持有 MAIN/LAUNCHER 的
 * activity 上对应 intent-filter 摘除，并给目标 activity 注入
 * MAIN/LAUNCHER + `exported=true`（API 31+ 有 intent-filter 必须显式 exported）。
 * null 表示不干预启动入口；目标 activity 不存在时安全降级（不摘除、不注入）。
 *
 * [applicationName] 为产物 application 的 android:name 覆盖（引擎 dex 存在时
 * 产物作为独立 androlua 应用运行，application 回落 `com.androlua.LuaApplication`）；
 * null 表示沿用模板 application。
 *
 * [removeActivities] 为产物中整体移除的 activity 全限定类名列表（引擎 dex 存在时
 * IDE MainActivity 残留声明会构成双桌面入口，需整体摘除）。
 *
 * [applicationIconId] 为产物注入的 application android:icon 资源 ID（**仅对模板无
 * icon 属性时兜底**，引用产物 arsc 中存在的 drawable/icon；Launcher 解析 TYPE_REFERENCE
 * 后读取 res/drawable/icon.png 文件条目——工程 icon.png 覆盖该条目即桌面图标生效。
 * 宿主自带图标后模板已声明 android:icon，属性原样保留、不重复注入）；
 * null 表示不干预（模板自声明 icon 属性原样保留，缺失则不注入）。
 */
data class ManifestEdit(
    val packageName: String,
    val appLabel: String,
    val versionCode: Int,
    val versionName: String,
    val usesPermissions: List<String>,
    val launcherActivity: String? = null,
    val applicationName: String? = null,
    val removeActivities: List<String> = emptyList(),
    val applicationIconId: Int? = null,
)

/**
 * aapt2 产物 AXML manifest 解析与补丁（纯字节级，无 Android 依赖）。
 *
 * 已按宿主真实产物逐字节核验的格式要点：
 * - 字符串池：u32 count@8, u32 flags@16（0=UTF-16，0x100=UTF-8），u32 stringsStart@20，
 *   u32 偏移表@28；UTF-16 条目 = u16 字符数 + 数据 + u16 终止符；UTF-8 条目 =
 *   变长长度前缀 × 2（charLen 在前、byteLen 在后，0x80 标志的两字节变长编码）+ 数据 + 0 终止。
 * - 属性：ns u32 + name u32（aapt2 风格 = 字符串池索引，前 32 个经 resource map 映射资源 ID）
 *   + rawValue u32 + Res_value（size u16=8, res0 u8=0, dataType u8, data u32）。
 * - 元素：0x0102，line@8 comment@12 ns@16 name@20 attrStart@24 attrSize@26 attrCount@28；
 *   属性数据位于 chunk+16+attrStart（attrStart=20）。
 * - 结束元素：0x0103，{line, comment, ns, name} 共 24 字节。
 * - 改包名：manifest 的 package 属性在宿主 debug 产物中存在（aapt2 也保留源 manifest
 *   package 属性）；无则新增（ns=0xFFFFFFFF 无命名空间）。与包名同前缀的自引用字符串
 *   （FileProvider authorities、androlua scheme host、DYNAMIC_RECEIVER 权限名）随池内
 *   原位替换一并更新，dex 类名（com.lioisme.lide.*）不动。
 */
object AXmlManifest {

    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_START_NS = 0x0100
    private const val CHUNK_END_NS = 0x0101
    private const val CHUNK_START_ELEMENT = 0x0102
    private const val CHUNK_END_ELEMENT = 0x0103

    private const val NO_INDEX = -1L
    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_BOOLEAN = 0x12

    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val MAIN_ACTION = "android.intent.action.MAIN"
    private const val LAUNCHER_CATEGORY = "android.intent.category.LAUNCHER"
    private const val DYNAMIC_RECEIVER_SUFFIX = ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

    private class Pool(val strings: MutableList<String>)

    private class ElementRef(
        val chunk: ByteArray,
        val line: Int,
        val ns: Int,
        val name: Int,
        val attrSize: Int,
        val attrs: List<Attr>,
    )

    private class Attr(
        val offset: Int,
        val ns: Int,
        val name: Int,
        val raw: Int,
        val size: Int,
        val res0: Int,
        val type: Int,
        val data: Long,
    )

    private class Parsed(val poolChunkIndex: Int, val chunks: List<ByteArray>)

    // ===== 读取入口 =====
    /** 现有 uses-permission 名称（文档序，去重）。 */
    fun readUsesPermissions(bytes: ByteArray): List<String> {
        return elementStringAttrs(bytes, "uses-permission", "name")
    }

    /** 宿主自声明的权限名（<permission> 元素，去重）。 */
    fun readSelfDeclaredPermissions(bytes: ByteArray): List<String> {
        return elementStringAttrs(bytes, "permission", "name")
    }

    /** manifest 的 package 属性值；缺失返回 null。 */
    fun readPackageName(bytes: ByteArray): String? {
        val parsed = parse(bytes)
        val strings = readPool(parsed.chunks[parsed.poolChunkIndex]).strings
        val manifest = parsed.chunks.firstOrNull { isElement(it, strings, "manifest") } ?: return null
        return stringAttr(parseElement(manifest), strings, "package")
    }

    /** 现持有 MAIN action 的 activity 全限定名（launcher 入口）；无则返回 null。 */
    fun readLauncherActivity(bytes: ByteArray): String? {
        val parsed = parse(bytes)
        val strings = readPool(parsed.chunks[parsed.poolChunkIndex]).strings
        return findMainLauncher(parsed.chunks, strings)?.first
    }

    /** 指定 activity 的 android:exported 值；无该属性返回 null。 */
    fun readExported(bytes: ByteArray, activityName: String): Boolean? {
        val parsed = parse(bytes)
        val strings = readPool(parsed.chunks[parsed.poolChunkIndex]).strings
        for (chunk in parsed.chunks) {
            if (!isElement(chunk, strings, "activity")) continue
            val el = parseElement(chunk)
            if (stringAttr(el, strings, "name") != activityName) continue
            val a = el.attrs.firstOrNull { strings.getOrNull(it.name) == "exported" } ?: return null
            return when {
                a.type != TYPE_INT_BOOLEAN -> null
                a.data != 0L -> true
                else -> false
            }
        }
        return null
    }

    private fun elementStringAttrs(bytes: ByteArray, element: String, attr: String): List<String> {
        val parsed = parse(bytes)
        val strings = readPool(parsed.chunks[parsed.poolChunkIndex]).strings
        val out = LinkedHashSet<String>()
        for (chunk in parsed.chunks) {
            if (!isElement(chunk, strings, element)) continue
            stringAttr(parseElement(chunk), strings, attr)?.let { out += it }
        }
        return out.toList()
    }

    /** provider 的 android:authorities 属性值按 <oldPackage>. 前缀重命名（类名不动）。 */
    private fun renameAuthorities(
        chunks: List<ByteArray>,
        strings: MutableList<String>,
        oldPackage: String,
        newPackage: String,
    ) {
        val authorities = LinkedHashSet<Int>()
        for (chunk in chunks) {
            if (typeOf(chunk) != CHUNK_START_ELEMENT) continue
            val el = parseElement(chunk)
            for (a in el.attrs) {
                val ns = strings.getOrNull(a.ns)
                val name = strings.getOrNull(a.name)
                if (ns == ANDROID_URI && name == "authorities" &&
                    a.type == TYPE_STRING && a.data in 0 until strings.size.toLong()
                ) {
                    authorities += a.data.toInt()
                }
            }
        }
        val prefix = oldPackage + "."
        for (i in authorities) {
            val s = strings[i]
            if (s.startsWith(prefix)) {
                strings[i] = newPackage + "." + s.substring(prefix.length)
            }
        }
    }

    // ===== 补丁入口 =====

    /**
     * 定位现持有 MAIN/LAUNCHER 的入口：
     * 返回（持有 activity 的 android:name，MAIN 所在 intent-filter START 的 chunk 下标）。
     * action 是 intent-filter 的直接子元素（无中间嵌套），故向上回退找最近的
     * intent-filter START 即其父元素。无 MAIN 时返回 null。
     */
    private fun findMainLauncher(
        chunks: List<ByteArray>,
        strings: List<String>,
    ): Pair<String?, Int>? {
        var currentActivity: String? = null
        for ((i, chunk) in chunks.withIndex()) {
            when (typeOf(chunk)) {
                CHUNK_START_ELEMENT -> {
                    val name = strings.getOrNull(u32(chunk, 20).toInt())
                    if (name == "activity") {
                        currentActivity = stringAttr(parseElement(chunk), strings, "name")
                    } else if (name == "action" &&
                        stringAttr(parseElement(chunk), strings, "name") == MAIN_ACTION
                    ) {
                        var j = i - 1
                        while (j >= 0) {
                            if (typeOf(chunks[j]) == CHUNK_START_ELEMENT &&
                                strings.getOrNull(u32(chunks[j], 20).toInt()) == "intent-filter"
                            ) {
                                return currentActivity to j
                            }
                            j--
                        }
                    }
                }

                CHUNK_END_ELEMENT -> {
                    if (strings.getOrNull(u32(chunk, 20).toInt()) == "activity") currentActivity = null
                }
            }
        }
        return null
    }

    /** 是否存在名为 [className] 的 activity 元素（用于 launcher 注入前的能力预检）。 */
    private fun hasActivity(
        chunks: List<ByteArray>,
        strings: List<String>,
        className: String,
    ): Boolean {
        for (chunk in chunks) {
            if (isElement(chunk, strings, "activity") &&
                stringAttr(parseElement(chunk), strings, "name") == className
            ) {
                return true
            }
        }
        return false
    }

    /** 按 [edit] 重写 manifest；池内原位替换包名相关字符串并追加新字符串。 */
    fun patch(bytes: ByteArray, edit: ManifestEdit): ByteArray {
        val parsed = parse(bytes)
        val pool = readPool(parsed.chunks[parsed.poolChunkIndex])
        val strings = pool.strings
        val idx = indexOf(strings)

        fun origIdx(s: String): Int? = idx[s]

        val oldPackage = readPackageName(bytes)

        // 1) 池内原位替换：包名与自引用权限名。
        if (oldPackage != null && oldPackage != edit.packageName) {
            origIdx(oldPackage)?.let { strings[it] = edit.packageName }
            val oldDyn = oldPackage + DYNAMIC_RECEIVER_SUFFIX
            val newDyn = edit.packageName + DYNAMIC_RECEIVER_SUFFIX
            if (oldDyn != newDyn) origIdx(oldDyn)?.let { strings[it] = newDyn }
            // provider authorities（如 androidx-startup 的 <applicationId>.androidx-startup）
            // 也随包名重命名；dex 类名（com.lioisme.lide.* 的组件名）不动。
            renameAuthorities(parsed.chunks, strings, oldPackage, edit.packageName)
        }

        // 2) 追加缺失字符串（含可能的 'package'/'label'/URI 等名）。
        val appendNeed = LinkedHashSet<String>()
        if (edit.packageName.isNotBlank() && origIdx("package") == null) appendNeed += "package"
        appendNeed += edit.packageName
        if (origIdx("label") == null) appendNeed += "label"
        appendNeed += edit.appLabel
        appendNeed += edit.versionName
        appendNeed += edit.usesPermissions
        if (edit.applicationName != null) appendNeed += edit.applicationName
        if (origIdx(ANDROID_URI) == null) appendNeed += ANDROID_URI
        if (origIdx("uses-permission") == null) appendNeed += "uses-permission"
        if (origIdx("name") == null) appendNeed += "name"
        if (edit.applicationIconId != null && origIdx("icon") == null) appendNeed += "icon"
        if (edit.launcherActivity != null) {
            // launcher 注入所需元素名/常量（宿主 manifest 通常已含，set 去重无副作用）。
            appendNeed += "intent-filter"
            appendNeed += "action"
            appendNeed += "category"
            appendNeed += "exported"
            appendNeed += MAIN_ACTION
            appendNeed += LAUNCHER_CATEGORY
        }
        val appended = LinkedHashMap<String, Int>()
        for (s in appendNeed) {
            if (idx[s] == null && appended[s] == null) appended[s] = strings.size + appended.size
        }
        strings += appended.keys
        fun newIdx(s: String): Int = idx[s] ?: appended.getValue(s)

        // 3) 重写元素：manifest 属性 / application label / uses-permission 过滤 / launcher 重定向。
        val kept = LinkedHashSet<String>()
        var appChunkIndex = -1
        val out = ArrayList<ByteArray>(parsed.chunks.size + 8)
        var pendingDropEnd = false

        // launcher 重定向：目标 activity 存在才动刀；目标即现入口时不摘除不注入。
        val launcherInfo =
            if (edit.launcherActivity != null &&
                hasActivity(parsed.chunks, strings, edit.launcherActivity)
            ) {
                findMainLauncher(parsed.chunks, strings)
            } else {
                null
            }
        val launcherFilterIndex = launcherInfo?.second ?: -1
        val launcherTargetIsCurrent = launcherInfo?.first == edit.launcherActivity
        var droppingLauncher = false
        var dropDepth = 0
        // removeActivities：整棵 activity 子树（含子元素与 END）跳过，深度计数退出。
        var droppingActivity = false
        var dropActivityDepth = 0

        for ((i, chunk) in parsed.chunks.withIndex()) {
            if (droppingLauncher) {
                when (typeOf(chunk)) {
                    CHUNK_START_ELEMENT -> dropDepth++
                    CHUNK_END_ELEMENT -> if (dropDepth == 0) droppingLauncher = false else dropDepth--
                }
                continue
            }
            if (droppingActivity) {
                when (typeOf(chunk)) {
                    CHUNK_START_ELEMENT -> dropActivityDepth++
                    CHUNK_END_ELEMENT ->
                        if (dropActivityDepth == 0) droppingActivity = false else dropActivityDepth--
                }
                continue
            }
            if (!launcherTargetIsCurrent && i == launcherFilterIndex) {
                // 摘除旧入口的 MAIN/LAUNCHER intent-filter 子树（含 action/category 子元素）。
                droppingLauncher = true
                continue
            }
            if (pendingDropEnd) {
                if (typeOf(chunk) == CHUNK_END_ELEMENT) pendingDropEnd = false
                continue
            }
            if (isElement(chunk, strings, "uses-permission")) {
                val el = parseElement(chunk)
                val perm = stringAttr(el, strings, "name")
                if (perm != null && edit.usesPermissions.contains(perm) && !kept.contains(perm)) {
                    kept += perm
                    out += chunk
                } else {
                    pendingDropEnd = true
                }
            } else if (isElement(chunk, strings, "manifest")) {
                out += rebuildManifest(chunk, strings, idx, ::newIdx, edit)
            } else if (isElement(chunk, strings, "application")) {
                appChunkIndex = out.size
                out += rebuildApplication(chunk, strings, idx, ::newIdx, edit)
            } else if (isElement(chunk, strings, "activity")) {
                val el = parseElement(chunk)
                val activityName = stringAttr(el, strings, "name")
                if (activityName != null && edit.removeActivities.contains(activityName)) {
                    // 引擎 dex 存在时：IDE MainActivity 残留声明构成双桌面入口，整棵移除。
                    droppingActivity = true
                    dropActivityDepth = 0
                } else if (edit.launcherActivity != null && activityName == edit.launcherActivity) {
                    // 新入口：exported=true + 注入 MAIN/LAUNCHER intent-filter。
                    out += rebuildLauncherActivity(chunk, strings, ::newIdx)
                    if (!launcherTargetIsCurrent) {
                        out += launcherIntentFilterChunks(::newIdx)
                    }
                } else {
                    out += chunk
                }
            } else {
                out += chunk
            }
        }

        // 4) 补齐缺失的 uses-permission（<application> 之前；无则文件尾）。
        val missing = edit.usesPermissions.filter { !kept.contains(it) }
        if (missing.isNotEmpty()) {
            val at = if (appChunkIndex >= 0) appChunkIndex else out.size
            val pairs = ArrayList<ByteArray>(missing.size * 2)
            for (perm in missing) {
                val pi = newIdx(perm)
                val uriIdx = newIdx(ANDROID_URI)
                val nameIdx = newIdx("name")
                val elem = emitElement(
                    newIdx("uses-permission"),
                    listOf(emitAttr(uriIdx, nameIdx, pi, TYPE_STRING, pi.toLong())),
                    line = 1,
                )
                pairs += elem
                pairs += emitEndElement(newIdx("uses-permission"))
            }
            out.addAll(at, pairs)
        }

        // 5) 重建文件。
        out[parsed.poolChunkIndex] = rebuildPool(strings)
        val total = out.sumOf { it.size } + 8
        val result = ByteArray(total)
        putU16(result, 0, 0x0003)
        putU16(result, 2, 8)
        putU32(result, 4, total.toLong())
        var p = 8
        for (c in out) {
            c.copyInto(result, p)
            p += c.size
        }
        return result
    }

    // ===== 元素重建 =====

    private fun rebuildManifest(
        chunk: ByteArray,
        strings: List<String>,
        idx: Map<String, Int>,
        newIdx: (String) -> Int,
        edit: ManifestEdit,
    ): ByteArray {
        val el = parseElement(chunk)
        val attrs = ArrayList<ByteArray>(el.attrs.size + 1)
        var hasPackage = false
        for (a in el.attrs) {
            attrs += when (strings.getOrNull(a.name)) {
                "versionCode" -> emitAttr(a.ns, a.name, NO_INDEX.toInt(), TYPE_INT_DEC, edit.versionCode.toLong())
                "versionName" -> {
                    val i = newIdx(edit.versionName)
                    emitAttr(a.ns, a.name, i, TYPE_STRING, i.toLong())
                }
                "package" -> {
                    hasPackage = true
                    if (edit.packageName.isBlank()) {
                        attrBytes(chunk, el, a)
                    } else {
                        val i = newIdx(edit.packageName)
                        emitAttr(a.ns, a.name, i, TYPE_STRING, i.toLong())
                    }
                }
                else -> attrBytes(chunk, el, a)
            }
        }
        if (!hasPackage && edit.packageName.isNotBlank()) {
            val i = newIdx(edit.packageName)
            attrs += emitAttr(NO_INDEX.toInt(), newIdx("package"), i, TYPE_STRING, i.toLong())
        }
        return emitElement(el.name, attrs, el.line)
    }

    private fun rebuildApplication(
        chunk: ByteArray,
        strings: List<String>,
        idx: Map<String, Int>,
        newIdx: (String) -> Int,
        edit: ManifestEdit,
    ): ByteArray {
        val el = parseElement(chunk)
        val attrs = ArrayList<ByteArray>(el.attrs.size + 1)
        var hasLabel = false
        var hasName = false
        var hasIcon = false
        for (a in el.attrs) {
            if (strings.getOrNull(a.name) == "label") {
                hasLabel = true
                val i = newIdx(edit.appLabel)
                attrs += emitAttr(a.ns, a.name, i, TYPE_STRING, i.toLong())
            } else if (edit.applicationName != null &&
                strings.getOrNull(a.name) == "name" &&
                strings.getOrNull(a.ns) == ANDROID_URI
            ) {
                // 引擎 dex 存在时：application 回落引擎 Application（独立 androlua 应用）。
                hasName = true
                val i = newIdx(edit.applicationName)
                attrs += emitAttr(a.ns, a.name, i, TYPE_STRING, i.toLong())
            } else {
                // 模板自声明的 android:icon 原样保留不重复注入（宿主 2026-08-09 起
                // 自带 @drawable/icon，即产物默认图标；工程 icon.png 覆盖条目数据生效）。
                if (strings.getOrNull(a.name) == "icon" && strings.getOrNull(a.ns) == ANDROID_URI) {
                    hasIcon = true
                }
                attrs += attrBytes(chunk, el, a)
            }
        }
        if (!hasLabel) {
            val i = newIdx(edit.appLabel)
            attrs += emitAttr(newIdx(ANDROID_URI), newIdx("label"), i, TYPE_STRING, i.toLong())
        }
        if (edit.applicationName != null && !hasName) {
            val i = newIdx(edit.applicationName)
            attrs += emitAttr(newIdx(ANDROID_URI), newIdx("name"), i, TYPE_STRING, i.toLong())
        }
        // 打包图标兜底：仅模板无 icon 属性时注入 android:icon 资源引用（TYPE_REFERENCE），
        // Launcher 经产物 arsc 定位 res/drawable/icon.png 条目——工程 icon.png 覆盖即生效。
        if (edit.applicationIconId != null && !hasIcon) {
            attrs += emitAttr(
                newIdx(ANDROID_URI),
                newIdx("icon"),
                NO_INDEX.toInt(),
                TYPE_REFERENCE,
                edit.applicationIconId.toLong(),
            )
        }
        return emitElement(el.name, attrs, el.line)
    }

    /**
     * 重建入口 activity 元素：android:exported 置 true（API 31+ 有 intent-filter
     * 必须显式 exported；TYPE_INT_BOOLEAN data=0xFFFFFFFF 为 true）。
     */
    private fun rebuildLauncherActivity(
        chunk: ByteArray,
        strings: List<String>,
        newIdx: (String) -> Int,
    ): ByteArray {
        val el = parseElement(chunk)
        val attrs = ArrayList<ByteArray>(el.attrs.size + 1)
        var hasExported = false
        for (a in el.attrs) {
            if (!hasExported && strings.getOrNull(a.name) == "exported" &&
                strings.getOrNull(a.ns) == ANDROID_URI
            ) {
                hasExported = true
                attrs += emitAttr(a.ns, a.name, NO_INDEX.toInt(), TYPE_INT_BOOLEAN, 0xFFFFFFFFL)
            } else {
                attrs += attrBytes(chunk, el, a)
            }
        }
        if (!hasExported) {
            attrs += emitAttr(newIdx(ANDROID_URI), newIdx("exported"), NO_INDEX.toInt(), TYPE_INT_BOOLEAN, 0xFFFFFFFFL)
        }
        return emitElement(el.name, attrs, el.line)
    }

    /**
     * 生成 MAIN/LAUNCHER intent-filter 元素序列（START/END 成对，
     * 紧随 activity START 元素之后写入即为该 activity 的子元素）。
     */
    private fun launcherIntentFilterChunks(newIdx: (String) -> Int): List<ByteArray> {
        val uri = newIdx(ANDROID_URI)
        val name = newIdx("name")
        val filter = newIdx("intent-filter")
        val action = newIdx("action")
        val category = newIdx("category")
        val mainIdx = newIdx(MAIN_ACTION)
        val launcherIdx = newIdx(LAUNCHER_CATEGORY)
        return listOf(
            emitElement(filter, emptyList(), 1),
            emitElement(action, listOf(emitAttr(uri, name, mainIdx, TYPE_STRING, mainIdx.toLong())), 1),
            emitEndElement(action),
            emitElement(category, listOf(emitAttr(uri, name, launcherIdx, TYPE_STRING, launcherIdx.toLong())), 1),
            emitEndElement(category),
            emitEndElement(filter),
        )
    }

    // ===== 字节工具 =====

    private fun typeOf(chunk: ByteArray): Int = u16(chunk, 0)

    private fun isElement(chunk: ByteArray, strings: List<String>, name: String): Boolean {
        return typeOf(chunk) == CHUNK_START_ELEMENT &&
            strings.getOrNull(u32(chunk, 20).toInt()) == name
    }

    private fun attrBytes(chunk: ByteArray, el: ElementRef, a: Attr): ByteArray {
        // 属性记录整块拷贝（attrStart 起 20 字节：ns/name/raw + Res_value），
        // 不能只拷 Res_value 的 a.size（8 字节），否则 name/type/data 被清零。
        return chunk.copyOfRange(a.offset, a.offset + el.attrSize)
    }

    private fun parseElement(chunk: ByteArray): ElementRef {
        val attrStart = u16(chunk, 24)
        val attrSize = u16(chunk, 26)
        val attrCount = u16(chunk, 28)
        val base = 16 + attrStart
        val attrs = ArrayList<Attr>(attrCount)
        for (a in 0 until attrCount) {
            val p = base + a * attrSize
            attrs += Attr(
                offset = p,
                ns = u32(chunk, p).toInt(),
                name = u32(chunk, p + 4).toInt(),
                raw = u32(chunk, p + 8).toInt(),
                size = u16(chunk, p + 12),
                res0 = chunk[p + 14].toInt() and 0xFF,
                type = chunk[p + 15].toInt() and 0xFF,
                data = u32(chunk, p + 16),
            )
        }
        return ElementRef(
            chunk = chunk,
            line = u32(chunk, 8).toInt(),
            ns = u32(chunk, 16).toInt(),
            name = u32(chunk, 20).toInt(),
            attrSize = attrSize,
            attrs = attrs,
        )
    }

    private fun stringAttr(el: ElementRef, strings: List<String>, attrName: String): String? {
        val a = el.attrs.firstOrNull { strings.getOrNull(it.name) == attrName } ?: return null
        if (a.type != TYPE_STRING || a.data !in 0 until strings.size.toLong()) return null
        return strings[a.data.toInt()]
    }

    private fun emitElement(nameIdx: Int, attrs: List<ByteArray>, line: Int): ByteArray {
        val out = ByteArray(36 + 20 * attrs.size)
        putU16(out, 0, CHUNK_START_ELEMENT)
        putU16(out, 2, 16)
        putU32(out, 4, out.size.toLong())
        putU32(out, 8, line.toLong())
        putU32(out, 12, NO_INDEX)
        putU32(out, 16, NO_INDEX)
        putU32(out, 20, nameIdx.toLong())
        putU16(out, 24, 20)
        putU16(out, 26, 20)
        putU16(out, 28, attrs.size)
        putU16(out, 30, 0)
        putU16(out, 32, 0)
        putU16(out, 34, 0)
        var p = 36
        for (a in attrs) {
            a.copyInto(out, p)
            p += 20
        }
        return out
    }

    private fun emitAttr(ns: Int, name: Int, raw: Int, type: Int, data: Long): ByteArray {
        val out = ByteArray(20)
        putU32(out, 0, ns.toLong())
        putU32(out, 4, name.toLong())
        putU32(out, 8, raw.toLong())
        putU16(out, 12, 8)
        out[14] = 0
        out[15] = type.toByte()
        putU32(out, 16, data)
        return out
    }

    private fun emitEndElement(nameIdx: Int): ByteArray {
        val out = ByteArray(24)
        putU16(out, 0, CHUNK_END_ELEMENT)
        putU16(out, 2, 16)
        putU32(out, 4, 24)
        putU32(out, 8, 1)
        putU32(out, 12, NO_INDEX)
        putU32(out, 16, NO_INDEX)
        putU32(out, 20, nameIdx.toLong())
        return out
    }

    // ===== 字符串池 =====

    private fun readPool(chunk: ByteArray): Pool {
        val count = u32(chunk, 8).toInt()
        val flags = u32(chunk, 16).toInt()
        val stringsStart = u32(chunk, 20).toInt()
        // styled string pool（0x400，aapt 旧版/style 资源）本解析器不实现：
        // 静默解析会产生错乱字符串（审计 §4.5），显式拒绝以便快速定位。
        if (flags and 0x400 != 0) throw AXmlException("styled string pool (0x400) not supported")
        val utf8 = (flags and 0x100) != 0
        val strings = ArrayList<String>(count)
        for (i in 0 until count) {
            val off = u32(chunk, 28 + 4 * i).toInt()
            val p = stringsStart + off
            strings += if (utf8) readUtf8(chunk, p) else readUtf16(chunk, p)
        }
        return Pool(strings)
    }

    private fun readUtf16(b: ByteArray, o: Int): String {
        val len = u16(b, o)
        return String(b, o + 2, len * 2, Charsets.UTF_16LE)
    }

    private fun readUtf8(b: ByteArray, o: Int): String {
        var p = o
        // AXML UTF-8 池条目 = 变长长度前缀 × 2（先 charLen 后 byteLen）+ UTF-8 数据 + 0 终止。
        // 与 aapt 的 ENCODE_LENGTH/decode_length 一致：首字节 < 0x80 时取单字节，
        // 否则高 7 位作长度高字节、紧随一字节作低字节（两字节变长编码）。
        val charLen = readUtf8Length(b, p)
        p += charLen.second
        val byteLen = readUtf8Length(b, p)
        p += byteLen.second
        return String(b, p, byteLen.first, Charsets.UTF_8)
    }

    /** 解码一个 AXML UTF-8 长度前缀，返回（长度, 占用字节数）。 */
    private fun readUtf8Length(b: ByteArray, o: Int): Pair<Int, Int> {
        val first = b[o].toInt() and 0xFF
        if (first and 0x80 == 0) return first to 1
        val len = (first and 0x7F) shl 8 or (b[o + 1].toInt() and 0xFF)
        return len to 2
    }

    private fun rebuildPool(strings: List<String>): ByteArray {
        val count = strings.size
        val offsets = IntArray(count)
        var dataSize = 0
        for (i in 0 until count) {
            offsets[i] = dataSize
            dataSize += 4 + strings[i].length * 2
        }
        val stringsStart = 28 + count * 4
        // Android 的 ResStringPool 校验 chunk size 必须 4 字节对齐
        // （"size ... is not on an integer boundary"），尾部补零对齐。
        val pad = (4 - (stringsStart + dataSize) % 4) % 4
        val out = ByteArray(stringsStart + dataSize + pad)
        putU16(out, 0, CHUNK_STRING_POOL)
        putU16(out, 2, 28)
        putU32(out, 4, out.size.toLong())
        putU32(out, 8, count.toLong())
        putU32(out, 12, 0)
        putU32(out, 16, 0)
        putU32(out, 20, stringsStart.toLong())
        putU32(out, 24, (stringsStart + dataSize).toLong())
        for (i in 0 until count) putU32(out, 28 + 4 * i, offsets[i].toLong())
        for (i in 0 until count) {
            val s = strings[i]
            val p = stringsStart + offsets[i]
            putU16(out, p, s.length)
            for (j in s.indices) putU16(out, p + 2 + 2 * j, s[j].code)
            putU16(out, p + 2 + s.length * 2, 0)
        }
        return out
    }

    // ===== 底层读写 =====

    private fun indexOf(strings: List<String>): Map<String, Int> {
        val m = HashMap<String, Int>(strings.size * 2)
        for (i in strings.indices) if (m[strings[i]] == null) m[strings[i]] = i
        return m
    }

    private fun parse(bytes: ByteArray): Parsed {
        var pos = 8
        val chunks = ArrayList<ByteArray>()
        var poolIndex = -1
        while (pos < bytes.size) {
            val type = u16(bytes, pos)
            val size = u32(bytes, pos + 4).toInt()
            if (size <= 0 || pos + size > bytes.size) throw AXmlException("bad chunk at $pos")
            val raw = bytes.copyOfRange(pos, pos + size)
            if (type == CHUNK_STRING_POOL) poolIndex = chunks.size
            chunks += raw
            pos += size
        }
        if (poolIndex < 0) throw AXmlException("no string pool")
        return Parsed(poolIndex, chunks)
    }

    private fun u16(b: ByteArray, o: Int): Int {
        return (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    }

    private fun u32(b: ByteArray, o: Int): Long {
        return (b[o].toLong() and 0xFF) or
            ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or
            ((b[o + 3].toLong() and 0xFF) shl 24)
    }

    private fun putU16(out: ByteArray, o: Int, v: Int) {
        out[o] = (v and 0xFF).toByte()
        out[o + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun putU32(out: ByteArray, o: Int, v: Long) {
        out[o] = (v and 0xFF).toByte()
        out[o + 1] = ((v shr 8) and 0xFF).toByte()
        out[o + 2] = ((v shr 16) and 0xFF).toByte()
        out[o + 3] = ((v shr 24) and 0xFF).toByte()
    }
}

package com.lioisme.lide.androlua

/** AXML（Android Binary XML）解析/补丁失败。 */
class AXmlException(message: String) : Exception(message)

/**
 * 打包产物 manifest 的补丁目标。
 *
 * [usesPermissions] 为**最终** uses-permission 全集：现有多余条目会被删除、
 * 缺失条目会在 `<application>` 之前补齐；宿主自声明权限（<permission> 元素）
 * 由调用方（ApkBuilder）并入本列表以保证运行时自洽。
 */
data class ManifestEdit(
    val packageName: String,
    val appLabel: String,
    val versionCode: Int,
    val versionName: String,
    val usesPermissions: List<String>,
)

/**
 * aapt2 产物 AXML manifest 解析与补丁（纯字节级，无 Android 依赖）。
 *
 * 已按宿主真实产物逐字节核验的格式要点：
 * - 字符串池：u32 count@8, u32 flags@16（0=UTF-16，0x100=UTF-8），u32 stringsStart@20，
 *   u32 偏移表@28；UTF-16 条目 = u16 字符数 + 数据 + u16 终止符；UTF-8 条目带长度高位变体。
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
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10

    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
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
        if (origIdx(ANDROID_URI) == null) appendNeed += ANDROID_URI
        if (origIdx("uses-permission") == null) appendNeed += "uses-permission"
        if (origIdx("name") == null) appendNeed += "name"
        val appended = LinkedHashMap<String, Int>()
        for (s in appendNeed) {
            if (idx[s] == null && appended[s] == null) appended[s] = strings.size + appended.size
        }
        strings += appended.keys
        fun newIdx(s: String): Int = idx[s] ?: appended.getValue(s)

        // 3) 重写元素：manifest 属性 / application label / uses-permission 过滤。
        val kept = LinkedHashSet<String>()
        var appChunkIndex = -1
        val out = ArrayList<ByteArray>(parsed.chunks.size + 8)
        var pendingDropEnd = false
        for (chunk in parsed.chunks) {
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
        for (a in el.attrs) {
            if (strings.getOrNull(a.name) == "label") {
                hasLabel = true
                val i = newIdx(edit.appLabel)
                attrs += emitAttr(a.ns, a.name, i, TYPE_STRING, i.toLong())
            } else {
                attrs += attrBytes(chunk, el, a)
            }
        }
        if (!hasLabel) {
            val i = newIdx(edit.appLabel)
            attrs += emitAttr(newIdx(ANDROID_URI), newIdx("label"), i, TYPE_STRING, i.toLong())
        }
        return emitElement(el.name, attrs, el.line)
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
        var byteLen = u16(b, p)
        p += 2
        var charLen = u16(b, p)
        p += 2
        if (byteLen and 0x8000 != 0) {
            byteLen = (byteLen and 0x7FFF) shl 16 or u16(b, p)
            p += 2
        }
        if (charLen and 0x8000 != 0) {
            charLen = (charLen and 0x7FFF) shl 16 or u16(b, p)
            p += 2
        }
        return String(b, p, byteLen, Charsets.UTF_8)
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

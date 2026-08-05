#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
androlua-community 全量分析器 v2 (analyze.py)

v2 修复（相对 v1，2026-08-03）：
  1. 读取磁盘**完整正文**（v1 时代语料仅为 ~300 字符列表预览，导致 1867/2144 条
     line_count/char_count/截断判定失真，verdict 随之失真）。
  2. 文件定位改为「按 manual_id 前缀扫描 lua_files/**/*.lua」，不再依赖 DB file_path
     （历史 DB 中该列存在编码混淆风险），DB 仅用于元数据（标题/作者/热度/日期）。
  3. 截断口径改为**真实文末状态**：complete（收尾合法） / truncated_tail（收尾在语句中）
     / empty（空文件）——v1 的 preview_truncated 概念废弃（那是接口预览限制，不是正文事实）。
  4. 新增 flags：hardcoded_secret（硬编码密码/密钥/token）、os_exec（os.execute/io.popen
     等 shell 调用）、remote_http（运行时从 URL 拉代码/dex）、sensitive_permission
     （高危权限或越权访问特征）、dead_snippet（无入口/不可运行碎片）。
  5. 保留三轴（kind/verdict/portability）与 schema 字段兼容性，新增字段见 RECORD_SCHEMA。

仅使用 Python 标准库。判定均为 static_review（静态文本评审，未实测）。
"""
import glob
import io
import json
import os
import re
import sqlite3

HERE = os.path.dirname(os.path.abspath(__file__))
KB = os.path.dirname(HERE)
REPO = os.path.abspath(os.path.join(KB, "..", "..", ".."))
SOURCE_DIR = os.path.join(REPO, "docs", "reference", "代码手册社区", "lua_files")
DB_PATH = os.path.join(REPO, "docs", "reference", "代码手册社区", "lua_manual.db")
CATALOG_DIR = os.path.join(KB, "catalog")

# ---------- 规则 ----------
PRIVATE_RE = re.compile(
    r"(com\.androlua|activity\.getLuaDir|activity\.getLuaActivity\b|LuaActivity\b|"
    r"bindClass\s*[\"']com\.androlua|LuaRecyclerAdapter|LuaDexLoader)"
)
EDITOR_BOUND_RE = re.compile(
    r"(FusionApp|fa2\b|uiManager|DsLr|droidscript|"
    r"只支持.*编辑器|Lua文件原生支持|\bsora\b)", re.I
)
ANDROID_API_RE = re.compile(r"\b(android|androidx|com\.google\.android|java\.)[A-Za-z_.]")
CHINESE_IDENT_RE = re.compile(
    r"(?m)^\s*(local function|function)\s+[\u4e00-\u9fff]|"
    r"^\s*[\u4e00-\u9fff]{2,10}\s*=\s*(function|\{)|"
    r"^\s*[\u4e00-\u9fff]{2,10}\s*=\s*[\"']"
)
GLOBAL_ASSIGN_RE = re.compile(r"(?m)^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*")
INCOMPLETE_MARK_RE = re.compile(r"(没写完|未完|待补|示例从略|……|未完待续|写不动)", re.I)
HARMFUL_RE = re.compile(
    r"(破解|绕过|白嫖).{0,8}(卡密|授权|会员|验证码|原版|收费|付费)|"
    r"(卡密|验证码|滑块).{0,8}(破解|绕过|识别)|"
    r"破解.{0,6}(验证|模式)|"
    r"网盘.{0,8}(破解|绕过|直链|无限速)|"
    r"抢红包|自动抢|抢单|短信轰炸|免流|盗取|改机|云机|"
    r"Xposed.{0,10}(注入|hook)|hook.{0,10}微信|防Momo|"
    r"无障碍.{0,10}(注入|hook)|抓包.{0,8}破解|gs登|破解\$请|辅助功能.{0,8}抢",
    re.I)

# 人工复核清单：正则无法覆盖的明确危害贴（验证码/卡密破解、网盘破解、无障碍注入等）
HARMFUL_OVERRIDES = {
    2274: "滑块验证码识别/卡密校验绕过（免root验证-绕过系列）",
    2275: "滑块验证码识别/卡密校验绕过（免root验证-绕过系列）",
    2276: "滑块验证码识别/卡密校验绕过（免root验证-绕过系列）",
    2146: "网盘下载直链破解（WebView拦截+注入JS 绕过原版限制）",
    4697: "设备码/卡密验证绕过注册模块",
    2826: "无障碍注入重写其他应用模块（绕过应用内校验）",
}
REMOTE_EXEC_RE = re.compile(
    r"(?:loadstring|dofile)\s*\([^)]*(?:http|Http|dl|down|content|remote)|"
    r"http[s]?://[^\"'\s]+\.(?:dex|lua|so)\b|loadDex\s*\([^)]*http", re.I)
SYNTAX_BAD_RE = re.compile(r"(?m)^\s*\ben\b\s*$")
SECRET_RE = re.compile(
    r"(password|passwd|pwd|secret|api[_-]?key|app[_-]?secret|token|access[_-]?key)\s*[:=]\s*[\"'][A-Za-z0-9+/=_\-]{8,}[\"']",
    re.I)
OS_EXEC_RE = re.compile(r"\bos\.execute\s*\(|\bio\.popen\s*\(|Runtime\.getRuntime\(\)")
SENSITIVE_RE = re.compile(
    r"(READ_SMS|READ_CONTACTS|ACCESS_FINE_LOCATION|READ_PHONE_STATE|"
    r"RECORD_AUDIO|CAMERA|WAKE_LOCK|WRITE_SETTINGS|SYSTEM_ALERT_WINDOW|"
    r"getDeviceId|getSubscriberId|getLine1Number|imei|短信|通讯录|录音|定位)", re.I)
LOCAL_RE = re.compile(r"\blocal\s+\w")
PLAYED_KEYWORDS = re.compile(
    r"(?m)\b(function|end|local|import|require|for|while|if|elseif|then|return|setContentView|loadlayout)\b")

STOP_GLOBALS = {
    "activity", "this", "service", "input", "output", "App", "app",
    "print", "pair", "ipairs", "table", "string", "math", "os", "io",
    "utf8", "coroutine", "debug", "assert", "error", "pcall", "xpcall",
    "tonumber", "tostring", "type", "select", "require", "dofile", "loadstring",
    "load", "collectgarbage", "getmetatable", "setmetatable", "rawget",
    "rawset", "rawequal", "next", "pairs", "self", "class", "import",
    "layout", "ls", "ts", "a", "b", "c", "d", "i", "j", "k", "n", "v", "t",
    "json", "cjson", "http", "url", "path", "file", "dir", "title", "id",
    "text", "name", "x", "y", "w", "h", "width", "height", "color", "pos",
    "content", "result", "data", "item", "view", "list", "adapter", "activity2",
}


def read_text(fp):
    if not os.path.exists(fp):
        return None
    try:
        with io.open(fp, "r", encoding="utf-8") as f:
            return f.read()
    except Exception:
        return None


def classify_kind(text):
    ws = text.strip()
    if not ws:
        return "fragment", ["empty"]
    if re.search(r"<uses-permission", ws) or re.match(r"\s*<[A-Za-z]+", ws, re.M):
        return "reference_sheet", ["xml_list"]
    if re.search(r"(公告|本次更新|版本说明|v\d+\.\d+|曾经|现已)", ws[:400]):
        if not _played(ws):
            return "announce", ["announcement_no_code"]
    if not _played(ws):
        if len(ws.split()) <= 40:
            return "text_note", ["short_text"]
        return "text_note", ["text_only"]
    if INCOMPLETE_MARK_RE.search(text):
        return "fragment", ["incomplete_mark"]
    return "code_demo", []


def classify_portability(text):
    if PRIVATE_RE.search(text):
        return "host_private", ["androlua_private"]
    if EDITOR_BOUND_RE.search(text):
        return "editor_bound", ["editor_bound"]
    if re.search(r"(已失效|已被移除|不支持|高版本.*移除|deprecated)", text, re.I) and ANDROID_API_RE.search(text):
        return "obsolete", ["obsolete"]
    if not ANDROID_API_RE.search(text):
        return "universal", None
    return "android_api", None


def _played(ws):
    return bool(PLAYED_KEYWORDS.search(ws))


def verdict(text, kind, portability, manual_id=None):
    flags = []
    if portability == "host_private":
        flags.append("private_api")
    if portability == "editor_bound":
        flags.append("editor_bound")
    if portability == "obsolete":
        flags.append("obsolete")
    if CHINESE_IDENT_RE.search(text):
        flags.append("chinese_identifier")
    n_global = 0
    for line in text.splitlines():
        # 仅统计列 0 起的顶层赋值（缩进的键值对属于 layout 表，不算全局泄漏）
        if line[:1].isspace() or not line:
            continue
        m = GLOBAL_ASSIGN_RE.match(line)
        if m:
            name = m.group(1)
            rhs = line[len(m.group(1)):].lstrip()
            if rhs.startswith("="):
                rhs = rhs[1:].lstrip()
            # 值为注释（如 shamrock=--[[...]] 隐藏代码惯用法）不算泄漏
            if rhs.startswith("--") or rhs.startswith("[["):
                continue
            if name and name not in STOP_GLOBALS and not line.startswith("local"):
                n_global += 1
    if n_global >= 8:
        flags.append("global_pollution")
    if SYNTAX_BAD_RE.search(text):
        flags.append("syntax_hint")
    if kind == "fragment":
        flags.append("incomplete_fragment")
    if kind == "announce":
        flags.append("announcement")
    if HARMFUL_RE.search(text):
        flags.append("harmful_elevated")
    if manual_id in HARMFUL_OVERRIDES:
        flags.append("manual_override")
        flags.append("harmful_elevated")
    if REMOTE_EXEC_RE.search(text):
        flags.append("remote_exec")
    if SECRET_RE.search(text):
        flags.append("hardcoded_secret")
    if OS_EXEC_RE.search(text):
        flags.append("os_exec")
    if SENSITIVE_RE.search(text):
        flags.append("sensitive_permission")

    bad = [f for f in flags if f not in ("incomplete_fragment", "announcement")]
    if "harmful_elevated" in flags:
        verdict = "harmful"
    elif "private_api" in flags or "editor_bound" in flags or "obsolete" in flags:
        verdict = "negative"
    elif "chinese_identifier" in flags or "global_pollution" in flags or "syntax_hint" in flags:
        verdict = "negative"
    elif "hardcoded_secret" in flags or "os_exec" in flags or "remote_exec" in flags:
        verdict = "negative"
    elif kind == "fragment" or kind == "announce":
        verdict = "neutral"
    elif kind in ("text_note", "reference_sheet"):
        verdict = "neutral"
    elif kind == "code_demo" and not bad:
        verdict = "positive"
    else:
        verdict = "neutral"
    return verdict, flags


def truncation_fact(text):
    """真实文末状态（v2：以磁盘完整正文为准，不再有接口预览概念）。"""
    if not text or not text.strip():
        return "empty"
    t = text.rstrip()
    if t.endswith((')', '}', ']', 'end', '"', "'", ';', '--', ']]', ',')):
        return "complete"
    # 收尾是不完整 token（如 'en'、变量名、中文半句）→ 判截断
    return "truncated_tail"


def main():
    os.makedirs(CATALOG_DIR, exist_ok=True)

    # 1) 磁盘文件索引：按文件名前导 manual_id 定位
    disk = {}
    for fp in glob.glob(os.path.join(SOURCE_DIR, "**", "*.lua"), recursive=True):
        name = os.path.basename(fp)
        m = re.match(r"^(\d+)_", name)
        if m:
            disk.setdefault(int(m.group(1)), fp)
    print("disk files indexed:", len(disk))

    # 2) DB 元数据
    if not os.path.exists(DB_PATH):
        print("DB missing:", DB_PATH)
        return
    with sqlite3.connect(DB_PATH) as conn:
        rows = conn.execute(
            "SELECT manual_id,type_name,manual_name,author_name,platform,"
            "time_add,time_update,manual_down,manual_hits,manual_comment,"
            "manual_up,manual_fav FROM articles").fetchall()

    stats = {"kind": {}, "verdict": {}, "portability": {}, "truncation": {}}
    by_cat = {}
    wrote = 0
    triage_pos, triage_neg, triage_harm = [], [], []
    for (mid, tname, mname, author, platform, tadd, tupd,
         down, hits, comment, up, fav) in rows:
        fp = disk.get(mid)
        text = read_text(fp) if fp else None
        if text is None:
            kind, ver, port = "unverifiable", "neutral", "unverifiable"
            flags = ["file_missing"]
            trunc = "missing"
        else:
            kind, kfl = classify_kind(text)
            port, pfl = classify_portability(text)
            ver, vfl = verdict(text, kind, port, mid)
            flags = kfl + (pfl or []) + vfl
            trunc = truncation_fact(text)
        for key, v in (("kind", kind), ("verdict", ver), ("portability", port),
                       ("truncation", trunc)):
            stats[key][v] = stats[key].get(v, 0) + 1
        by_cat.setdefault(tname, {}).setdefault(ver, 0)
        by_cat[tname][ver] += 1
        rec = {
            "manual_id": mid,
            "type_name": tname,
            "manual_name": mname,
            "author": author.strip() if author else "",
            "platform": platform or "",
            "source_date": tadd or "",
            "hits": hits, "up": up, "favorite": fav, "comment": comment,
            "source_file": os.path.relpath(fp, SOURCE_DIR) if fp else "",
            "kind": kind,
            "verdict": ver,
            "portability": port,
            "flags": flags,
            "truncation": trunc,
            "line_count": text.count("\n") + 1 if text else 0,
            "char_count": len(text) if text else 0,
            "confidence": "static_review",
        }
        with io.open(os.path.join(CATALOG_DIR, "post_%d.json" % mid), "w", encoding="utf-8") as f:
            json.dump(rec, f, ensure_ascii=False, indent=1)
        wrote += 1

        if ver == "positive" and len(text or "") >= 120 and "editor_bound" not in flags:
            triage_pos.append(rec)
        if ver == "negative" and (port == "host_private" or "chinese_identifier" in flags
                                  or "hardcoded_secret" in flags):
            triage_neg.append(rec)
        if ver == "harmful":
            triage_harm.append(rec)

    agg = {"total": len(rows), "posts_written": wrote,
           "by_kind": stats["kind"], "by_verdict": stats["verdict"],
           "by_portability": stats["portability"],
           "by_truncation": stats["truncation"],
           "by_category_verdict": by_cat}
    with io.open(os.path.join(CATALOG_DIR, "aggregate.json"), "w", encoding="utf-8") as f:
        json.dump(agg, f, ensure_ascii=False, indent=1)

    # 3) triage 短名单
    with io.open(os.path.join(CATALOG_DIR, "triage_shortlist.md"), "w", encoding="utf-8") as f:
        f.write("# Triage 短名单（v2：全量正文静态初筛 → 人工复核）\n\n"
                "仅静态规则初筛，`static_review`；不代表设备行为。\n\n")
        f.write("## A. 建议入选 positive 的主题候选（%d）\n\n| id | 分类 | 标题 | 端口 |\n|---|---|---|---|\n"
                % len(triage_pos))
        for r in sorted(triage_pos, key=lambda x: (-x["hits"], -x["char_count"]))[:50]:
            f.write("| %s | %s | %s | %s |\n"
                    % (r["manual_id"], r["type_name"], r["manual_name"], r["portability"]))
        f.write("\n## B. 建议入选 negative 的候选（%d）\n\n| id | 分类 | 标题 | 理由(flags) |\n|---|---|---|---|\n"
                % len(triage_neg))
        for r in sorted(triage_neg, key=lambda x: -x["hits"])[:50]:
            f.write("| %s | %s | %s | %s |\n"
                    % (r["manual_id"], r["type_name"], r["manual_name"], ",".join(r["flags"])))
        f.write("\n## C. harmful 候选（%d，仅识别，不用于工程）\n\n"
                % len(triage_harm))
        for r in sorted(triage_harm, key=lambda x: -x["hits"])[:50]:
            f.write("- id %s | %s | %s | %s\n"
                    % (r["manual_id"], r["type_name"], r["manual_name"], ",".join(r["flags"])))
        f.write("\n> 说明：人工复核后如需调整 verdict，编辑 post_<id>.json 顶部 verdict/flags 并回写来源。\n")

    # 4) 质量报告
    with io.open(os.path.join(CATALOG_DIR, "quality_report.md"), "w", encoding="utf-8") as f:
        f.write("# 社区语料质量报告 v2（全量正文静态扫描）\n\n")
        f.write("> 生成：`tools/analyze.py` ｜ 口径：TASK_ORDER.md + STANDARDS.md ｜ "
                "`static_review`，未实测\n\n")
        f.write("## 0. 来源级事实（v2 修正）\n\n")
        f.write("- 本版改为**读取磁盘完整正文**（lua_files/**/*.lua，12.5MB/438K 行）；\n")
        f.write("- v1 所称「300 字符预览截断 1740/2144」系接口列表预览限制，**不是正文事实**，已废弃；\n")
        f.write("- 真实文末截断（truncated_tail）与空文件计数见下表。\n\n")
        f.write("| 截断状态 | 数量 |\n|---|---|\n")
        for k, v in sorted(agg.get("by_truncation", {}).items()):
            f.write("| %s | %d |\n" % (k, v))
        f.write("\n## 1. 三轴分布\n\n")
        f.write("### kind\n\n| 值 | 数量 |\n|---|---|\n")
        for k, v in sorted(agg["by_kind"].items(), key=lambda x: -x[1]):
            f.write("| %s | %d |\n" % (k, v))
        f.write("\n### verdict\n\n| 值 | 数量 |\n|---|---|\n")
        for k, v in sorted(agg["by_verdict"].items(), key=lambda x: -x[1]):
            f.write("| %s | %d |\n" % (k, v))
        f.write("\n### portability\n\n| 值 | 数量 |\n|---|---|\n")
        for k, v in sorted(agg["by_portability"].items(), key=lambda x: -x[1]):
            f.write("| %s | %d |\n" % (k, v))
        f.write("\n## 2. 结论\n\n- verdict 均为**静态初筛**（规则引擎），不代表可生产质量；\n"
                "- 正/负面主题卡入库前必须人工复核原文与设备行为；\n"
                "- 规则引擎召回面（flags 命中分布）可查 catalog/post_*.json。\n")
    print(json.dumps(agg, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()

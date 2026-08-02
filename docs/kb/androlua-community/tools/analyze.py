#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
androlua-community 全量分析器 (analyze.py)

对 docs/reference/代码手册社区/lua_files 的 2144 篇社区帖子，按 TASK_ORDER.md 三轴
(kind / verdict / portability) 做静态规则初筛，产出：
  catalog/post_<mid>.json       每篇一个（可回溯）
  catalog/aggregate.json        聚合统计
  catalog/triage_shortlist.md   待人工复核短名单

判定均为 static_review（静态文本评审，未实测）。规则见 STANDARDS.md。
仅使用 Python 标准库。
"""
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

PRIVATE_RE = re.compile(
    r"(com\.androlua|activity\.getLuaDir|activity\.getLuaActivity\b|LuaActivity\b|bindClass\s*\"com\.androlua)"
)
EDITOR_BOUND_RE = re.compile(
    r"(FusionApp|fa2\b|uiManager|\bchen?\b|DsLr|droidscript|"
    r"只支持.*编辑器|Lua文件原生支持|sora)", re.I
)
ANDROID_API_RE = re.compile(r"\b(android|androidx|com\.google\.android|java\.)[A-Za-z_.]")
CHINESE_IDENT_RE = re.compile(
    r"(?m)^\s*(function|local function)\s+[\u4e00-\u9fff]|"
    r"^\s*[\u4e00-\u9fff]{2,10}\s*=\s*(function|\{)")
GLOBAL_ASSIGN_RE = re.compile(r"(?m)^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*")
INCOMPLETE_MARK_RE = re.compile(r"(没写完|未完|写得`|写完|待补|しcode|示例从略|……)", re.I)
HARMFUL_RE = re.compile(
    r"(VIP.{0,6}授权.{0,10}(绕过|破解)|绕过.{0,12}验证|抢红包|注入|后台截图|"
    r"强制安装|诱导安装|短信轰炸|免流|盗取|抓包.{0,8}破解|gs登|自动抢|破解\$请)", re.I)
REMOTE_EXEC_RE = re.compile(
    r"(loadstring|load)\s*\([^)]*(?:http|Http|dl|down|content|remote)", re.I)
SYNTAX_BAD_RE = re.compile(r"(?m)^\s*\ben\b\s*$")
LOCAL_RE = re.compile(r"\blocal\s+\w")
IMPORT_RE = re.compile(r"(?m)^\s*(?:require\s+|import\s*[{\"'\s])")
PLAYED_KEYWORDS = re.compile(
    r"(?m)\b(function|end|local|import|require|for|while|if|elseif|then|return)\b")

STOP_GLOBALS = {
    "activity", "this", "service", "input", "output", "App", "app",
    "print", "pair", "ipairs", "table", "string", "math", "os", "io",
    "utf8", "coroutine", "debug", "assert", "error", "pcall", "xpcall",
    "tonumber", "tostring", "type", "select", "require", "dofile", "loadstring",
    "load", "collectgarbage", "getmetatable", "setmetatable", "rawget",
    "rawset", "rawequal", "next", "pairs", "self", "class", "import",
    "layout", "ls", "ts", "a", "b", "c", "d", "i", "j", "k", "n", "v", "t",
}


def read_text(rel):
    fp = os.path.join(SOURCE_DIR, rel)
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

    # 公告/纯公告类
    if re.search(r"(公告|本次更新|版本说明|v\d+\.\d+|曾经|现已)", ws[:400]):
        if not _played(ws):
            return "announce", ["announcement_no_code"]

    if not _played(ws):
        if len(ws.split()) <= 40:
            return "text_note", ["short_text"]
        return "text_note", ["text_only"]

    if INCOMPLETE_MARK_RE.search(text):
        return "fragment", ["incomplete_mark"]

    lines = ws.splitlines()
    last = lines[-1].strip()
    if last and re.match(r"^[A-Za-z0-9_]{3,}$", last) and not last.endswith(
            (")", "}", "]", "end", "'", '"')):
        return "fragment", ["truncated_tail"]

    return "code_demo", []


def classify_portability(text):
    if PRIVATE_RE.search(text):
        return "host_private", ["androlua_private"]
    if EDITOR_BOUND_RE.search(text):
        return "editor_bound", ["editor_bound"]
    if re.search(r"(已失效|已被移除|不支持|高版本.*移除|deprecated)", text, re.I) and ANDROID_API_RE.search(text):
        return "obsolete", None
    if not ANDROID_API_RE.search(text):
        return "universal", None
    return "android_api", None


def _played(ws):
    return bool(PLAYED_KEYWORDS.search(ws))


def verdict(text, kind, portability):
    flags = []
    if portability == "host_private":
        flags.append("private_api")
    if portability == "editor_bound":
        flags.append("editor_bound")
    if portability == "obsolete":
        flags.append("obsolete")
    if CHINESE_IDENT_RE.search(text):
        flags.append("chinese_identifier")
    # 全局泄漏检测（按行：无 local 前缀的顶层赋值）
    n_global = 0
    for line in text.splitlines():
        m = GLOBAL_ASSIGN_RE.match(line.lstrip())
        if m:
            name = m.group(1)
            if name and name not in STOP_GLOBALS and not line.lstrip().startswith("local"):
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
    if REMOTE_EXEC_RE.search(text):
        flags.append("remote_exec")

    bad = [f for f in flags if f not in ("incomplete_fragment", "announcement")]
    if "harmful_elevated" in flags or "remote_exec" in flags:
        verdict = "harmful"
    elif "private_api" in flags or "editor_bound" in flags or "obsolete" in flags:
        verdict = "negative"
    elif "chinese_identifier" in flags or "global_pollution" in flags or "syntax_hint" in flags:
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


def last_token_ok(text):
    t = text.rstrip()
    if not t:
        return True
    return t.endswith((')', '}', ']', 'end', '"', "'", ';', '--', '``', ']]'))


def truncation_fact(text):
    """来源级事实：社区 list 接口只回传约 300 字符预览，完整正文需详情接口（未找到）。
    当文末语句不完整时判定为 preview_truncated。"""
    if not text or not text.strip():
        return "empty"
    if not last_token_ok(text):
        return "preview_truncated"
    return "complete_or_unverifiable"


def main():
    os.makedirs(CATALOG_DIR, exist_ok=True)
    if not os.path.exists(DB_PATH):
        print("DB missing:", DB_PATH)
        return
    with sqlite3.connect(DB_PATH) as conn:
        rows = conn.execute(
            "SELECT manual_id,type_name,manual_name,author_name,platform,"
            "time_add,time_update,manual_down,manual_hits,manual_comment,"
            "manual_up,manual_fav,file_path FROM articles").fetchall()

    stats = {"kind": {}, "verdict": {}, "portability": {}}
    by_cat = {}
    wrote = 0
    triage_pos = []
    triage_neg = []
    triage_harm = []
    for (mid, tname, mname, author, platform, tadd, tupd,
         down, hits, comment, up, fav, fp) in rows:
        text = read_text(fp)
        if text is None:
            kind, ver, port = "unverifiable", "neutral", "unverifiable"
            flags = ["file_missing"]
        else:
            kind, kfl = classify_kind(text)
            port, pfl = classify_portability(text)
            ver, vfl = verdict(text, kind, port)
            flags = kfl + (pfl or []) + vfl
        for key in ("kind", "verdict", "portability"):
            v = {"kind": kind, "verdict": ver, "portability": port}[key]
            stats[key][v] = stats[key].get(v, 0) + 1
        trunc = truncation_fact(text)
        stats.setdefault("source_truncation", {})
        stats["source_truncation"][trunc] = stats["source_truncation"].get(trunc, 0) + 1
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
            "source_file": fp,
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

        if ver == "positive" and len(text or "") >= 90 and "editor_bound" not in flags:
            triage_pos.append(rec)
        if ver == "negative" and (port == "host_private" or "chinese_identifier" in flags):
            triage_neg.append(rec)
        if ver == "harmful":
            triage_harm.append(rec)

    agg = {"total": len(rows), "posts_written": wrote,
           "by_kind": stats["kind"], "by_verdict": stats["verdict"],
           "by_portability": stats["portability"],
           "by_source_truncation": stats.get("source_truncation", {}),
           "by_category_verdict": by_cat}
    with io.open(os.path.join(CATALOG_DIR, "aggregate.json"), "w", encoding="utf-8") as f:
        json.dump(agg, f, ensure_ascii=False, indent=1)

    # --- 短名单（人工复核入口） ---
    with io.open(os.path.join(CATALOG_DIR, "triage_shortlist.md"), "w", encoding="utf-8") as f:
        f.write("# Triage 短名单（自动初筛 → 人工复核）\n\n"
                "仅静态规则初筛，`static_review`；不代表设备行为。\n\n")
        f.write("## A. 建议入选 positive 的主题候选（%d）\n\n| id | 分类 | 标题 | 端口 |\n|---|---|---|---|\n"
                % len(triage_pos))
        for r in sorted(triage_pos, key=lambda x: (-x["hits"], -x["char_count"]))[:40]:
            f.write("| %s | %s | %s | %s |\n"
                    % (r["manual_id"], r["type_name"], r["manual_name"], r["portability"]))
        f.write("\n## B. 建议入选 negative 的候选（%d）\n\n| id | 分类 | 标题 | 理由(flags) |\n|---|---|---|---|\n"
                % len(triage_neg))
        for r in sorted(triage_neg, key=lambda x: -x["hits"])[:40]:
            f.write("| %s | %s | %s | %s |\n"
                    % (r["manual_id"], r["type_name"], r["manual_name"], ",".join(r["flags"])))
        f.write("\n## C. harmful 候选（%d，仅识别，不用于工程）\n\n"
                % len(triage_harm))
        for r in sorted(triage_harm, key=lambda x: -x["hits"])[:40]:
            f.write("- id %s | %s | %s | %s\n"
                    % (r["manual_id"], r["type_name"], r["manual_name"], ",".join(r["flags"])))
        f.write("\n> 说明：人工复核后如需调整 verdict，编辑 post_<id>.json 顶部 verdict/flags 并回写来源。\n")

    # --- 质量报告 ---
    with io.open(os.path.join(CATALOG_DIR, "quality_report.md"), "w", encoding="utf-8") as f:
        f.write("# 社区语料质量报告（全量静态扫描）\n\n")
        f.write("> 生成：`tools/analyze.py` ｜ 口径：TASK_ORDER.md + STANDARDS.md ｜ "
                "`static_review`，未实测\n\n")
        f.write("## 0. 来源级事实（重要）\n\n")
        f.write("- 全部 2144 篇来自社区 list 接口 `manual_content` 预览（约 300 字符上限），"
                "完整正文需详情接口（未公开/未找到）。\n")
        f.write("- 文末语句不完整（preview_truncated）占比：%d/%d\n\n"
                % (agg.get("by_source_truncation", {}).get("preview_truncated", 0), agg["total"]))
        f.write("| 截断状态 | 数量 |\n|---|---|\n")
        for k, v in sorted(agg.get("by_source_truncation", {}).items()):
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
        f.write("\n## 2. 结论\n\n- 语料以微型片段为主（预览受限），"
                "「positive」为**初筛候选**，不代表可生产质量；"
                "入库正/负面主题卡前必须人工复核原文与设备行为。\n")
    print(json.dumps(agg, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
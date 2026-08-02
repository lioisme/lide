#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_training.py — 从 catalog/ 批量生成 LLM 训练候选样本（JSONL）

规则（training/README.md §3）：
- preview_truncated 的帖子不生成 analyze/refactor 主体样本（仅记录）；
- harmful 只生成 reject 候选；
- 输出带 "draft": true 标记，需人工复核后才可进入精选集。
仅使用标准库。
"""
import io
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
KB = os.path.dirname(HERE)
CATALOG = os.path.join(KB, "catalog")
OUT = os.path.join(KB, "training", "drafts", "batch_candidates.jsonl")

FLAG_TO_ISSUE = {
    "chinese_identifier": "中文/拼音标识符命名不规范",
    "global_pollution": "无 local 的全局变量泄漏",
    "syntax_hint": "疑似语法错误（如 en/end、缺 then）",
    "private_api": "绑定宿主私有类 com.androlua.*，跨版本不稳定",
    "editor_bound": "依赖 FusionApp/fa2 等编辑器专属能力",
    "obsolete": "引用已废弃/失效 API",
    "incomplete_fragment": "正文残缺（预览截断或作者未写完）",
    "harmful_elevated": "存在授权绕过/辅助滥用等有害特征",
    "remote_exec": "运行时下载并加载 dex/远程代码",
}

VERDICT_ZH = {
    "positive": "正面",
    "negative": "负面",
    "neutral": "中性",
    "harmful": "有害",
    "unverifiable": "无法判定",
}


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    samples = []
    stats = {"analyze": 0, "reject": 0, "skipped_truncated": 0, "skipped_short": 0}
    for name in sorted(os.listdir(CATALOG)):
        if not name.startswith("post_") or not name.endswith(".json"):
            continue
        with io.open(os.path.join(CATALOG, name), encoding="utf-8") as f:
            r = json.load(f)
        if r.get("truncation") == "preview_truncated":
            stats["skipped_truncated"] += 1
            continue
        if r.get("char_count", 0) < 40:
            stats["skipped_short"] += 1
            continue
        flags = r.get("flags", [])
        if r.get("verdict") == "harmful":
            samples.append({
                "type": "reject",
                "instruction": "以下社区代码被判定为有害实践，请说明拒绝理由与替代方案。",
                "input": "帖子《%s》(manual_id=%d) 标签：%s" % (
                    r["manual_name"], r["manual_id"], ",".join(flags)),
                "output": "拒绝：%s。该样本属 harmful 类别，仅记录识别特征，不传播实现。"
                          "替代建议：改用公开 API 的合规实现。" %
                          "；".join([FLAG_TO_ISSUE.get(x, x) for x in flags
                                     if x in FLAG_TO_ISSUE]) or "有害特征",
                "meta": {
                    "manual_id": r["manual_id"], "title": r["manual_name"],
                    "source_truncated": False, "verdict": "harmful",
                    "flags": flags, "confidence": "static_review",
                },
                "draft": True,
            })
            stats["reject"] += 1
            continue
        issues = [FLAG_TO_ISSUE[x] for x in flags if x in FLAG_TO_ISSUE]
        verdict_zh = VERDICT_ZH.get(r.get("verdict"), r.get("verdict"))
        samples.append({
            "type": "analyze",
            "instruction": "请评审以下 AndroLua 社区代码片段，给出三轴判定与问题清单。",
            "input": "《%s》(manual_id=%d, 分类=%s)\n%s" % (
                r["manual_name"], r["manual_id"], r["type_name"],
                "（正文见 catalog 对应源文件，此处为元数据）"),
            "output": "kind=%s；verdict=%s（%s）；portability=%s。%s" % (
                r.get("kind", "?"), r.get("verdict", "?"), verdict_zh,
                r.get("portability", "?"),
                "问题：" + "；".join(issues) if issues else "未发现明确问题（人工复核确认）"),
            "meta": {
                "manual_id": r["manual_id"], "title": r["manual_name"],
                "source_truncated": False, "verdict": r.get("verdict"),
                "flags": flags, "confidence": "static_review",
            },
            "draft": True,
        })
        stats["analyze"] += 1

    with io.open(OUT, "w", encoding="utf-8") as f:
        for s in samples:
            f.write(json.dumps(s, ensure_ascii=False) + "\n")
    print(json.dumps(stats, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
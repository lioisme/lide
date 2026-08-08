#!/usr/bin/env bash
# 校验 app/vendor/androlua 与上游 AndroLua Pro 的全树差异 ⊆ 台账白名单
# （scripts/upstream-pristine.json，与 app/vendor/androlua/UPSTREAM.md 对应的机器可读副本）。
# CI 用（ubuntu，verify-vendor job）；本地 Windows 用 verify-upstream-pristine.ps1。
# 用法: bash scripts/verify-upstream-pristine.sh [-u <已解压上游根>]
#   缺省自动从 GitHub 下载 upstreamRef 对应 tarball（不落地仓库）。
set -u
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JSON="$REPO_ROOT/scripts/upstream-pristine.json"
UPSTREAM_ROOT=""
while getopts "u:" opt; do
  case "$opt" in
    u) UPSTREAM_ROOT="$OPTARG" ;;
    *) echo "用法: $0 [-u 上游根目录]" >&2; exit 2 ;;
  esac
done

PY=$(command -v python3 || command -v python || echo "")
if [ -z "$PY" ]; then echo "FAIL: 需要 python3 解析白名单 JSON"; exit 2; fi
read_json() { "$PY" -c "
import json,sys
cfg=json.load(open(sys.argv[1],encoding='utf-8'))
print(cfg[sys.argv[2]])
" "$JSON" "$1"; }

VENDOR_ROOT="$REPO_ROOT/$(read_json vendorRoot)"
UPSTREAM_JAVA_REL="$(read_json upstreamJavaRoot)"
REF="$(read_json upstreamRef)"

if [ ! -d "$VENDOR_ROOT" ]; then echo "FAIL: vendor Java 根不存在: $VENDOR_ROOT"; exit 2; fi

# ---------- 上游树获取 ----------
if [ -z "$UPSTREAM_ROOT" ]; then
  WORK="$(mktemp -d)"
  trap 'rm -rf "$WORK"' EXIT
  URL="https://codeload.github.com/nirenr/AndroLua_pro/zip/$REF"
  echo "下载上游树: $URL"
  curl -fsSL -o "$WORK/upstream.zip" "$URL" || { echo "FAIL: 上游树下载失败（离线请用 -u 提供）"; exit 2; }
  (cd "$WORK" && unzip -q upstream.zip) || { echo "FAIL: 解压失败"; exit 2; }
  UPSTREAM_ROOT="$(find "$WORK" -maxdepth 1 -type d -name 'AndroLua_pro-*' | head -1)"
  [ -n "$UPSTREAM_ROOT" ] || { echo "FAIL: 未找到解压目录（结构漂移）"; exit 2; }
fi
UPSTREAM_ROOT="$UPSTREAM_ROOT/$UPSTREAM_JAVA_REL"
if [ ! -d "$UPSTREAM_ROOT" ]; then echo "FAIL: 上游 Java 根不存在: $UPSTREAM_ROOT"; exit 2; fi

# ---------- 白名单 ----------
MODIFIED=$(read_json modified)
DELETED=$(read_json deleted)

FAILURES=0
ok() { :; }

diff_nonempty() { # $1=up $2=local → 0=无差异
  git diff --no-index --ignore-cr-at-eol -w --quiet "$1" "$2" >/dev/null 2>&1
  [ $? -ne 0 ]
}

# ---------- 1. local-only + 2. 共有文件差异 ----------
while IFS= read -r f; do
  rel="${f#$VENDOR_ROOT/}"
  up="$UPSTREAM_ROOT/$rel"
  if [ ! -f "$up" ]; then
    if echo "$MODIFIED" | grep -qF "\"$rel\""; then
      echo "FAIL 白名单冲突: modified 条目在 upstream 侧不存在（清单录入错误）: $rel"; FAILURES=$((FAILURES+1))
    else
      echo "FAIL LOCAL-ONLY（上游不存在且不在白名单）: $rel"; FAILURES=$((FAILURES+1))
    fi
    continue
  fi
  if diff_nonempty "$up" "$f"; then
    if echo "$MODIFIED" | grep -qF "\"$rel\""; then
      : # 白名单文件，允许
    else
      echo "FAIL MODIFIED（实质差异且不在白名单）: $rel"; FAILURES=$((FAILURES+1))
    fi
  fi
done < <(find "$VENDOR_ROOT" -type f -name '*.java')

# ---------- 3. 上游独有（deleted 白名单） ----------
while IFS= read -r f; do
  rel="${f#$UPSTREAM_ROOT/}"
  if [ ! -f "$VENDOR_ROOT/$rel" ]; then
    if echo "$DELETED" | grep -qF "\"$rel\""; then
      : # 白名单删除，允许
    else
      echo "FAIL DELETED（上游有、vendor 已删且不在白名单）: $rel"; FAILURES=$((FAILURES+1))
    fi
  fi
done < <(find "$UPSTREAM_ROOT" -type f -name '*.java')

echo ""
echo "=== verify-upstream-pristine（上游 $REF）==="
if [ "$FAILURES" -gt 0 ]; then
  echo "FAIL: $FAILURES 项漂移；白名单与台账: scripts/upstream-pristine.json / app/vendor/androlua/UPSTREAM.md"
  exit 1
fi
echo "OK: vendor 全树差异 ⊆ 台账白名单（24 modified + 42 deleted，零 local-only）"
exit 0

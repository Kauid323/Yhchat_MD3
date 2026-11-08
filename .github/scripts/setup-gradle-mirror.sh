#!/usr/bin/env bash
#
# 给 gradle-wrapper.properties 的 distributionUrl 做国内镜像兜底。
#
# 背景：services.gradle.org 在 CI 网络上经常 504 / read timeout，
# 而 gradlew 首次启动必须下发行包（9.4.1-all 约 250MB），一卡整条流水线就挂。
#
# 行为：
#   1. 探官方 URL：能拿到 2xx/3xx 就**原样不动**（网络正常时与以前完全一致）；
#   2. 官方不可达时，按 腾讯云 → 阿里云 → downloads.gradle.org 顺序找可用镜像；
#   3. 全都不行就保持原样 exit 0，交给 gradlew 自己再试，不额外制造失败。
#
# 探测为什么用 HEAD 而不是 Range：
#   上一版用 `curl -r 0-1023`，GitHub runner 上三个源全返回 000 —— 部分镜像/CDN
#   对 Range 请求直接 400 或断连，于是被误判成"不可达"，脚本空转退出，
#   gradlew 照样连官方源超时。现在 HEAD 优先，仅在 HEAD 被拒时才退到 1 字节 Range。
#
# 用法：
#   bash .github/scripts/setup-gradle-mirror.sh [wrapper 路径]
#
set -uo pipefail

WRAPPER="${1:-gradle/wrapper/gradle-wrapper.properties}"

if [ ! -f "$WRAPPER" ]; then
  echo "wrapper not found: $WRAPPER (skip mirror setup)" >&2
  exit 0
fi

# properties 里冒号是转义的（https\://...），读出来先还原成正常 URL
CURRENT_URL=$(sed -n 's/^distributionUrl=//p' "$WRAPPER" | tr -d '\\')
if [ -z "$CURRENT_URL" ]; then
  echo "distributionUrl not found in $WRAPPER (skip mirror setup)" >&2
  exit 0
fi
FILE_NAME=$(basename "$CURRENT_URL")
echo "wrapper distribution: $CURRENT_URL"
echo "distribution file   : $FILE_NAME"

probe() {
  local url="$1" code
  # 1) HEAD：最轻，且大多数 CDN 支持
  code=$(curl -sIL --max-time 15 -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || true)
  case "${code:-000}" in
    2* | 3*) printf '%s' "$code"; return 0 ;;
  esac
  # 2) HEAD 被拒时退到 1 字节 Range（取 1 字节就够判断可达性，不用 1KB）
  code=$(curl -sL --max-time 15 -o /dev/null -w '%{http_code}' --range 0-0 "$url" 2>/dev/null || true)
  printf '%s' "${code:-000}"
}

is_ok() {
  case "$1" in
    200 | 206 | 301 | 302 | 307 | 308) return 0 ;;
    *) return 1 ;;
  esac
}

OFFICIAL_CODE=$(probe "$CURRENT_URL")
echo "probe official -> $OFFICIAL_CODE"
if is_ok "$OFFICIAL_CODE"; then
  echo "official source reachable, keeping distributionUrl as is"
  exit 0
fi

MIRRORS=(
  "https://mirrors.cloud.tencent.com/gradle/$FILE_NAME"
  "https://mirrors.aliyun.com/macports/distfiles/gradle/$FILE_NAME"
  "https://downloads.gradle.org/distributions/$FILE_NAME"
)

for MIRROR in "${MIRRORS[@]}"; do
  CODE=$(probe "$MIRROR")
  echo "probe $MIRROR -> $CODE"
  if is_ok "$CODE"; then
    # 用 python3 写转义：bash 的 ${v//:/\\:} 和 sed 的 's/:/\\:/g' 都会把反斜杠
    # 吞掉，落盘的仍是裸冒号，java.util.Properties 会把 value 从冒号处截断。
    python3 - "$WRAPPER" "$MIRROR" <<'PY'
import sys

path, url = sys.argv[1], sys.argv[2]
escaped = url.replace(":", "\\:")

with open(path, encoding="utf-8") as f:
    lines = f.read().splitlines()

out = [
    ("distributionUrl=" + escaped) if line.startswith("distributionUrl=") else line
    for line in lines
]

with open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(out) + "\n")
PY
    echo "distributionUrl switched to $MIRROR"
    echo "--- $WRAPPER ---"
    cat "$WRAPPER"
    exit 0
  fi
done

echo "no reachable Gradle mirror, keeping the official distributionUrl" >&2
# 不 fail：让 gradlew 自己再试一次，别把网络问题变成脚本失败
exit 0
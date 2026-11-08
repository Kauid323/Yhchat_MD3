#!/usr/bin/env bash
#
# 给 gradle-wrapper.properties 的 distributionUrl 做国内镜像兜底。
#
# 背景：services.gradle.org 在大陆网络下经常 504 或 read timeout，
# gradlew 第一次启动就要下发行包（9.4.1-all 约 250MB），一卡就是整条流水线失败。
#
# 行为：
#   1. 先探官方 URL 的前 1KB，能拿到 2xx/3xx 就原样保留，**什么都不改**
#      （保证 CI 在网络正常时跟以前完全一致）；
#   2. 官方不可达时，按「腾讯云 → 阿里云」顺序找可用镜像，命中就改写 wrapper；
#   3. 都不行就保持原样退出 0，交给 gradlew 自己再试一次，不额外制造失败。
#
# 用法：
#   bash .github/scripts/setup-gradle-mirror.sh [wrapper 路径]
#
set -uo pipefail

WRAPPER="${1:-gradle/wrapper/gradle-wrapper.properties}"
PROBE_BYTES=1024
PROBE_TIMEOUT=20

if [ ! -f "$WRAPPER" ]; then
  echo "wrapper not found: $WRAPPER (skip mirror setup)" >&2
  exit 0
fi

# properties 文件里冒号是转义的（https\://...），读出来先还原成正常 URL
CURRENT_URL=$(sed -n 's/^distributionUrl=//p' "$WRAPPER" | sed 's/\\//g')
if [ -z "$CURRENT_URL" ]; then
  echo "distributionUrl not found in $WRAPPER (skip mirror setup)" >&2
  exit 0
fi
FILE_NAME=$(basename "$CURRENT_URL")
echo "wrapper distribution: $CURRENT_URL"

probe() {
  curl -sS -L --max-time "$PROBE_TIMEOUT" -r "0-$((PROBE_BYTES - 1))" \
    -o /dev/null -w '%{http_code}' "$1" 2>/dev/null || true
}

is_ok() {
  case "$1" in
    200 | 206 | 301 | 302 | 307 | 308) return 0 ;;
    *) return 1 ;;
  esac
}

OFFICIAL_CODE=$(probe "$CURRENT_URL")
echo "probe official -> ${OFFICIAL_CODE:-<no response>}"
if is_ok "$OFFICIAL_CODE"; then
  echo "official source reachable, keeping distributionUrl as is"
  exit 0
fi

MIRRORS=(
  "https://mirrors.cloud.tencent.com/gradle/$FILE_NAME"
  "https://mirrors.aliyun.com/macports/distfiles/gradle/$FILE_NAME"
)

for MIRROR in "${MIRRORS[@]}"; do
  CODE=$(probe "$MIRROR")
  echo "probe $MIRROR -> ${CODE:-<no response>}"
  if is_ok "$CODE"; then
    # 改写 wrapper：用 python3 处理，别用 sed/参数扩展 ——
    # bash 的 ${var//:/\\:} 和 sed 的 \\: 都会把反斜杠吞掉，写进文件的还是裸冒号，
    # 而 java.util.Properties 里 value 段的冒号转义是 \: ，必须真的落盘一个反斜杠。
    python3 - "$WRAPPER" "$MIRROR" <<'PY'
import sys

path, url = sys.argv[1], sys.argv[2]
escaped = url.replace(":", "\\:")

with open(path, encoding="utf-8") as f:
    lines = f.read().splitlines()

out = []
for line in lines:
    if line.startswith("distributionUrl="):
        out.append("distributionUrl=" + escaped)
    else:
        out.append(line)

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
exit 0
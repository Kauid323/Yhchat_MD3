#!/usr/bin/env bash
#
# 将 GitHub Actions 构建失败的报错详情通过云湖（Yunhu）bot-send 接口发送出去。
#
# 只发能定位问题的报错，不发过程日志、不发日志附件：
#   - Kotlin/Java 编译错误行（e: path:line:col 消息）
#   - 没有编译错误时退回 Gradle 的 "* What went wrong" 原因段
#
# 依赖环境变量（取值方式与 workflow 中的 notify-bot 保持一致）：
#   BOT_SEND_TOKEN_VAR / BOT_SEND_TOKEN_SECRET   云湖 bot token（优先 vars，其次 secrets）
#   BOT_RECV_ID_VAR    / BOT_RECV_ID_SECRET      接收方 id
#   BOT_RECV_TYPE_VAR  / BOT_RECV_TYPE_SECRET    接收方类型
#   GH_TOKEN                                     需要 actions:read 权限，用于拉取失败日志
#   GITHUB_REPOSITORY / GITHUB_RUN_ID / GITHUB_WORKFLOW / GITHUB_REF_NAME / GITHUB_SHA / GITHUB_ACTOR
#
# 可选环境变量：
#   WORKFLOW_LABEL        消息里展示的 workflow 名称
#   BOT_ERROR_MAX_CHARS   错误内容最大字符数（默认 3000）

set -uo pipefail

BOT_SEND_TOKEN="${BOT_SEND_TOKEN_VAR:-${BOT_SEND_TOKEN_SECRET:-}}"
BOT_RECV_ID="${BOT_RECV_ID_VAR:-${BOT_RECV_ID_SECRET:-}}"
BOT_RECV_TYPE="${BOT_RECV_TYPE_VAR:-${BOT_RECV_TYPE_SECRET:-}}"

if [ -z "$BOT_SEND_TOKEN" ] || [ -z "$BOT_RECV_ID" ] || [ -z "$BOT_RECV_TYPE" ]; then
  echo "Skip bot failure notify"
  echo "YHCHAT_BOT_SEND_TOKEN: $([ -n "$BOT_SEND_TOKEN" ] && echo present || echo missing)"
  echo "YHCHAT_BOT_RECV_ID: $([ -n "$BOT_RECV_ID" ] && echo present || echo missing)"
  echo "YHCHAT_BOT_RECV_TYPE: $([ -n "$BOT_RECV_TYPE" ] && echo present || echo missing)"
  exit 0
fi

REPO="${GITHUB_REPOSITORY:-}"
RUN_ID="${GITHUB_RUN_ID:-}"
WORKFLOW_NAME="${WORKFLOW_LABEL:-${GITHUB_WORKFLOW:-GitHub Actions}}"
BRANCH="${GITHUB_REF_NAME:-}"
ACTOR="${GITHUB_ACTOR:-}"
SHA="${GITHUB_SHA:-}"
SHORT_SHA="${SHA:0:7}"
RUN_URL="https://github.com/${REPO}/actions/runs/${RUN_ID}"
MESSAGE_MAX_CHARS="${BOT_ERROR_MAX_CHARS:-3000}"

WORK_DIR="$(mktemp -d)"
FAILED_LOG="${WORK_DIR}/failed.log"
: > "$FAILED_LOG"

COMMIT_SUBJECT="$(git log -1 --pretty=%s 2>/dev/null || true)"

# ---------------------------------------------------------------------------
# 拉取失败日志
# ---------------------------------------------------------------------------
FAILED_JOB_IDS=""

collect_run_jobs() {
  [ -n "$REPO" ] && [ -n "$RUN_ID" ] || return 1
  local jobs_json
  jobs_json="$(gh run view "$RUN_ID" --repo "$REPO" --json jobs 2>"${WORK_DIR}/gh-jobs.err" || true)"
  [ -n "$jobs_json" ] || return 1

  # 只取失败 job 的 id，用来按 job 拉日志；失败步骤清单不再进消息，就不解析了
  FAILED_JOB_IDS="$(printf '%s' "$jobs_json" | jq -r '.jobs[]? | select(.conclusion == "failure") | .databaseId // empty' 2>/dev/null || true)"
}

fetch_failed_logs() {
  # 优先按 job 拉取：单个 job 完成后其日志即可用，不受整个 run 未结束影响。
  if [ -n "$FAILED_JOB_IDS" ]; then
    while IFS= read -r job_id; do
      [ -n "$job_id" ] || continue
      gh api "/repos/${REPO}/actions/jobs/${job_id}/logs" >> "$FAILED_LOG" 2>>"${WORK_DIR}/gh-jobs.err" || true
      printf '\n' >> "$FAILED_LOG"
    done <<< "$FAILED_JOB_IDS"
  fi

  if [ -s "$FAILED_LOG" ]; then
    return 0
  fi

  # 兜底一：整轮 run 的失败步骤日志。
  if gh run view "$RUN_ID" --repo "$REPO" --log-failed > "$FAILED_LOG" 2>>"${WORK_DIR}/gh-jobs.err" \
     && [ -s "$FAILED_LOG" ]; then
    return 0
  fi

  # 兜底二：下载整轮 run 的日志包。
  local zip="${WORK_DIR}/run-logs.zip" dir="${WORK_DIR}/run-logs"
  if gh api "/repos/${REPO}/actions/runs/${RUN_ID}/logs" > "$zip" 2>>"${WORK_DIR}/gh-jobs.err" && [ -s "$zip" ]; then
    mkdir -p "$dir"
    if unzip -o -q "$zip" -d "$dir" >/dev/null 2>&1; then
      find "$dir" -type f -name '*.txt' -print0 | sort -z | xargs -0 -r cat > "$FAILED_LOG" 2>/dev/null || true
    fi
  fi

  [ -s "$FAILED_LOG" ]
}

collect_run_jobs || true
if ! fetch_failed_logs; then
  echo "未能获取失败日志，仅发送基本信息" >&2
  cat "${WORK_DIR}/gh-jobs.err" >&2 2>/dev/null || true
fi

# ---------------------------------------------------------------------------
# 抽取报错详情
#
# 只保留真正能定位问题的那几行，不做模糊 grep：
#   1) Kotlin/Java 编译错误   e: .../Foo.kt:12:34 Unresolved reference 'X'
#   2) Gradle 的 What went wrong 段（失败任务 + 原因链）
# 其余 > Task / Downloading / 环境变量之类的过程输出一律丢掉 ——
# 那些才是把消息撑成一大坨、让人根本找不到报错的原因。
#
# 注意：`gh api .../logs` 返回的日志每行都带
#   "2026-11-08T06:19:53.8802666Z " 这样的时间戳前缀，
# 不去掉的话所有 ^e: / ^> Task 之类的锚点都匹配不上。
# ---------------------------------------------------------------------------
ERROR_EXCERPT=""

# 先把时间戳前缀剥掉，得到干净日志
CLEAN_LOG="${WORK_DIR}/clean.log"
sed -E 's/^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:.]+Z ?//' "$FAILED_LOG" > "$CLEAN_LOG" 2>/dev/null || : > "$CLEAN_LOG"

# 1) 编译错误：把 runner 上的绝对路径压成仓库内相对路径，行号列号保留
COMPILER_ERRORS="$(
  grep -a -E '^e: (file://)?/' "$CLEAN_LOG" 2>/dev/null \
    | sed -E 's#^e: (file://)?/[^:]*/((app|live|player|buildSrc)/src/)#e: \2#' \
    | awk '!seen[$0]++' \
    | head -n 60 || true
)"

# 2) Gradle 失败原因：只取 "* What went wrong:" 之后的内容，跳过 "* Try:"
GRADLE_REASON="$(
  awk '
    /\* What went wrong:/ { f = 1; next }
    /\* Try:/ { f = 0 }
    f { print }
  ' "$CLEAN_LOG" 2>/dev/null \
    | sed -E 's#/home/runner/work/[^/]+/[^/]+/##g' \
    | awk 'NF' \
    | head -n 10 || true
)"

# 3) 编译错误最有用；没有就退回 Gradle 原因
if [ -n "$COMPILER_ERRORS" ]; then
  ERROR_EXCERPT="$COMPILER_ERRORS"
elif [ -n "$GRADLE_REASON" ]; then
  ERROR_EXCERPT="$GRADLE_REASON"
fi

# 4) 兜底：什么结构化信息都没抓到，只留带 error/failed 的行
if [ -z "$ERROR_EXCERPT" ] && [ -s "$CLEAN_LOG" ]; then
  ERROR_EXCERPT="$(
    grep -a -i -E '\b(error|failed|exception|caused by)\b' "$CLEAN_LOG" 2>/dev/null \
      | grep -a -v -E '^> Task|Downloading|^\.|Welcome to Gradle|^$|^##\[group\]' \
      | awk '!seen[$0]++' \
      | tail -n 20 || true
  )"
fi

truncate_tail() {
  local text="$1" max="$2"
  if [ "${#text}" -gt "$max" ]; then
    printf '%s\n...(已截断)' "${text: -$max}"
  else
    printf '%s' "$text"
  fi
}

# 选择比日志里最长连续反引号更长的围栏，避免代码块被日志内容截断。
compute_fence() {
  local text="$1" max
  max="$(printf '%s' "$text" | grep -o '`\+' | awk 'BEGIN { m = 2 } { if (length($0) > m) m = length($0) } END { print m + 1 }')"
  case "$max" in
    ''|*[!0-9]*) max=3 ;;
  esac
  printf '`%.0s' $(seq 1 "$max")
}

# 只发报错详情：一句标题 + 提交定位 + 错误内容。不要 Changelog、不要失败任务清单。
build_message() {
  local fence
  fence="$(compute_fence "$ERROR_EXCERPT")"

  printf '### ❌ %s 构建失败\n\n' "$WORKFLOW_NAME"
  printf '`%s` %s' "${SHORT_SHA:-unknown}" "${BRANCH:-unknown}"
  if [ -n "$COMMIT_SUBJECT" ]; then
    printf ' · %s' "$COMMIT_SUBJECT"
  fi
  printf '\n'

  if [ -n "$ERROR_EXCERPT" ]; then
    printf '\n%s\n' "$fence"
    truncate_tail "$ERROR_EXCERPT" "$MESSAGE_MAX_CHARS"
    printf '\n%s\n' "$fence"
    printf '\n[完整日志](%s)\n' "$RUN_URL"
  else
    printf '\n未能自动抓取报错内容，请查看 Run 日志：%s\n' "$RUN_URL"
  fi
}

# ---------------------------------------------------------------------------
# 发送
# ---------------------------------------------------------------------------
send_markdown() {
  local text="$1" payload
  payload=$(jq -n \
    --arg recvId "$BOT_RECV_ID" \
    --arg recvType "$BOT_RECV_TYPE" \
    --arg text "$text" \
    '{
      recvId: $recvId,
      recvType: $recvType,
      contentType: "markdown",
      content: {
        text: $text
      }
    }')

  curl --fail-with-body -sS \
    -X POST "https://chat-go.jwzhd.com/open-apis/v1/bot/send?token=${BOT_SEND_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$payload"
}

MESSAGE="$(build_message)"

# 只发一条纯报错文本。失败日志附件不再发送 ——
# 那玩意儿动辄几 MB，内容全是 > Task 过程输出，翻半天找不到问题所在。
if send_markdown "$MESSAGE"; then
  echo "已发送报错详情"
else
  echo "发送报错详情失败" >&2
fi

exit 0

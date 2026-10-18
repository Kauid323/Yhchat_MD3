#!/usr/bin/env bash
#
# 将 GitHub Actions 构建失败的报错日志通过云湖（Yunhu）bot-send 接口发送出去。
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
#   BOT_ERROR_MAX_CHARS   消息内错误片段最大字符数（默认 3000）
#   BOT_ERROR_MAX_BYTES   附件日志最大字节数（默认 10MB）

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
FILE_MAX_BYTES="${BOT_ERROR_MAX_BYTES:-10485760}"

WORK_DIR="$(mktemp -d)"
FAILED_LOG="${WORK_DIR}/failed.log"
ATTACH_LOG="${WORK_DIR}/failed-log.txt"
: > "$FAILED_LOG"

COMMIT_SUBJECT="$(git log -1 --pretty=%s 2>/dev/null || true)"

# ---------------------------------------------------------------------------
# 拉取失败日志
# ---------------------------------------------------------------------------
FAILED_STEPS=""
FAILED_JOB_IDS=""

collect_run_jobs() {
  [ -n "$REPO" ] && [ -n "$RUN_ID" ] || return 1
  local jobs_json
  jobs_json="$(gh run view "$RUN_ID" --repo "$REPO" --json jobs 2>"${WORK_DIR}/gh-jobs.err" || true)"
  [ -n "$jobs_json" ] || return 1

  FAILED_JOB_IDS="$(printf '%s' "$jobs_json" | jq -r '.jobs[]? | select(.conclusion == "failure") | .databaseId // empty' 2>/dev/null || true)"
  FAILED_STEPS="$(printf '%s' "$jobs_json" | jq -r '
    [ .jobs[]? | select(.conclusion == "failure") |
      "#### \(.name)\n" +
      (( [ .steps[]? | select(.conclusion == "failure") | "- `\(.name)`" ] | join("\n") ))
    ] | join("\n\n")
  ' 2>/dev/null || true)"
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
# 组装消息
# ---------------------------------------------------------------------------
ERROR_EXCERPT=""
if [ -s "$FAILED_LOG" ]; then
  ERROR_EXCERPT="$(grep -a -i -E '(error|failed|failure|exception|caused by|fatal|what went wrong|execution failed|build failed|could not|no such file|not found|permission denied)' "$FAILED_LOG" \
    | tail -n 200 || true)"
  if [ -z "$ERROR_EXCERPT" ]; then
    ERROR_EXCERPT="$(tail -n 200 "$FAILED_LOG" || true)"
  fi
fi

truncate_tail() {
  local text="$1" max="$2"
  if [ "${#text}" -gt "$max" ]; then
    printf '...(日志过长，仅显示末尾 %s 个字符)...\n%s' "$max" "${text: -$max}"
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

build_message() {
  local fence
  fence="$(compute_fence "$ERROR_EXCERPT")"

  printf '## ❌ %s 构建失败\n\n' "$WORKFLOW_NAME"
  printf -- '- **分支**: %s\n' "${BRANCH:-unknown}"
  printf -- '- **提交**: `%s`' "${SHORT_SHA:-unknown}"
  if [ -n "$COMMIT_SUBJECT" ]; then
    printf ' %s' "$COMMIT_SUBJECT"
  fi
  printf '\n'
  printf -- '- **触发者**: %s\n' "${ACTOR:-unknown}"
  printf -- '- **Run**: [查看完整日志与产物](%s)\n' "$RUN_URL"

  if [ -n "$FAILED_STEPS" ]; then
    printf '\n### 失败任务\n%s\n' "$FAILED_STEPS"
  fi

  if [ -n "$ERROR_EXCERPT" ]; then
    printf '\n### 错误日志\n%s\n' "$fence"
    truncate_tail "$ERROR_EXCERPT" "$MESSAGE_MAX_CHARS"
    printf '\n%s\n' "$fence"
    printf '\n_以上为关键错误片段，完整日志见附件。_\n'
  else
    printf '\n> 未能自动抓取到失败日志，请点击上方 Run 链接查看。\n'
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

send_file() {
  local file_path="$1" file_name upload_response file_key payload
  file_name=$(basename "$file_path")

  upload_response=$(curl --fail-with-body -sS \
    -X POST "https://chat-go.jwzhd.com/open-apis/v1/file/upload?token=${BOT_SEND_TOKEN}" \
    -F "file=@${file_path};filename=${file_name}") || return 1

  file_key=$(printf '%s' "$upload_response" | jq -r '.data.fileKey // empty')
  if [ -z "$file_key" ]; then
    echo "Upload did not return fileKey for ${file_name}" >&2
    return 1
  fi

  payload=$(jq -n \
    --arg recvId "$BOT_RECV_ID" \
    --arg recvType "$BOT_RECV_TYPE" \
    --arg fileKey "$file_key" \
    '{
      recvId: $recvId,
      recvType: $recvType,
      contentType: "file",
      content: {
        fileKey: $fileKey
      }
    }')

  curl --fail-with-body -sS \
    -X POST "https://chat-go.jwzhd.com/open-apis/v1/bot/send?token=${BOT_SEND_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$payload"
}

MESSAGE="$(build_message)"

if send_markdown "$MESSAGE"; then
  echo "已发送失败通知"
else
  echo "发送失败通知（markdown）失败" >&2
fi

if [ -s "$FAILED_LOG" ]; then
  head -c "$FILE_MAX_BYTES" "$FAILED_LOG" > "$ATTACH_LOG"
  if send_file "$ATTACH_LOG"; then
    echo "已发送失败日志附件"
  else
    echo "发送失败日志附件失败" >&2
  fi
fi

exit 0

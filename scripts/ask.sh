#!/bin/sh
# Ask Claude one support question on one customer's ticket, through our MCP
# server.
#   scripts/ask.sh CUST-17 \
#     "I was charged twice for order 1043, please refund the duplicate" [out.jsonl]
# Claude Code gets no built-in tools, only this server's four. The ticket token
# travels in the Authorization header on every MCP call.
set -e
CUSTOMER=$1; QUESTION=$2; OUT=${3:-/dev/null}
case "$OUT" in /*) ;; *) OUT="$PWD/$OUT" ;; esac
DIR=$(cd "$(dirname "$0")/.." && pwd)
TOKEN=$("$DIR/scripts/token.py" ticket "$CUSTOMER")
CONF=$(mktemp)
cat > "$CONF" <<JSON
{"mcpServers": {"payments": {
  "type": "http",
  "url": "${MCP_URL:-http://localhost:8420/mcp}",
  "headers": {"Authorization": "Bearer $TOKEN"}}}}
JSON
PROMPT="You are a support agent for a payments company, \
working one customer's ticket. Use the payments tools. The customer writes:

$QUESTION"
cd "$(mktemp -d)"
# --setting-sources project: no user-level settings, so no personal plugins,
# hooks or skills leak into the session. Every run sees the same clean Claude
# Code.
TOOLS="mcp__payments__recent_payments mcp__payments__payment_detail
mcp__payments__request_refund mcp__payments__search_policy"
claude -p "$PROMPT" --model "${MODEL:-claude-sonnet-5}" --tools "" \
  --setting-sources project \
  --allowedTools ${ALLOWED:-$TOOLS} \
  ${DISALLOWED:+--disallowedTools $DISALLOWED} \
  --mcp-config "$CONF" --strict-mcp-config --disable-slash-commands --no-chrome \
  --output-format stream-json --verbose </dev/null | tee "$OUT" \
  | python3 -c "import json,sys
for l in sys.stdin:
    try: e=json.loads(l)
    except: continue
    if e.get('type')=='result': print(e.get('result',''))"
rm -f "$CONF"

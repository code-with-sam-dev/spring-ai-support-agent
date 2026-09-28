#!/bin/sh
# What transport did the server actually start? Two runs, one with the protocol
# left unset and one as configured, each probed on both endpoints.
#   scripts/transport-check.sh
# Needs Postgres up (docker compose up -d) and a built jar.
set -e
DIR=$(cd "$(dirname "$0")/.." && pwd); cd "$DIR"
export $(cat .env.local)
JAVA_HOME=${JAVA_HOME_25:-$HOME/.sdkman/candidates/java/25.0.4-amzn}
probe() {  # probe <label> [extra args]
  LOG=$(mktemp); PORT=8431
  "$JAVA_HOME/bin/java" -jar target/support-agent-0.0.1-SNAPSHOT.jar --server.port=$PORT "$@" > "$LOG" 2>&1 &
  PID=$!
  until grep -qE "Started SupportAgent|APPLICATION FAILED|Exception in thread" "$LOG"; do sleep 1; done
  TOKEN=$(scripts/token.py ticket CUST-17)
  for path in /mcp /sse; do
    printf '$ curl %s  ->  ' "$path"
    curl -s -o /dev/null -m 3 -w 'HTTP %{http_code}\n' -H "Authorization: Bearer $TOKEN" \
      -H 'Accept: application/json, text/event-stream' "localhost:$PORT$path" || echo "no answer"
  done
  kill $PID; wait $PID 2>/dev/null || true; rm -f "$LOG"
}
printf '### protocol left unset\n'
probe --spring.ai.mcp.server.protocol=
printf '### protocol=STREAMABLE, as configured\n'
probe

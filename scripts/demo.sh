#!/bin/sh
# Every demonstration in the video, run for real, command then output.
#   scripts/demo.sh > demo.txt
# Starts Postgres and the server from a fresh database, then runs each section.
# The video's terminal frames are generated from this output, never typed.
set -e
DIR=$(cd "$(dirname "$0")/.." && pwd); cd "$DIR"
[ -f .env.local ] || python3 -c "import secrets;print('SUPPORT_JWT_SECRET='+secrets.token_hex(32))" > .env.local
export $(cat .env.local)
JAVA_HOME=${JAVA_HOME:-$HOME/.sdkman/candidates/java/25.0.4-amzn}; export JAVA_HOME
LOG=${LOG:-/tmp/support-agent-demo.log}

pkill -f support-agent-0.0.1 2>/dev/null || true; sleep 2
docker compose down -v >/dev/null 2>&1; docker compose up -d >/dev/null 2>&1
until docker compose exec -T postgres pg_isready -U support >/dev/null 2>&1 </dev/null; do sleep 1; done; sleep 2
./mvnw -q -DskipTests package >/dev/null
"$JAVA_HOME/bin/java" -jar target/support-agent-0.0.1-SNAPSHOT.jar > "$LOG" 2>&1 &
until grep -qE "Started SupportAgent|APPLICATION FAILED" "$LOG" 2>/dev/null; do sleep 2; done

section() { printf '\n### %s\n' "$1"; }
run() { printf '$ %s\n' "$1"; sh -c "$1" </dev/null; }
sql() { docker compose exec -T postgres psql -U support -tA -c "$1" </dev/null; }
TICKET=$(scripts/token.py ticket CUST-17); export TICKET
LEAD=$(scripts/token.py lead); export LEAD

section tools
run 'scripts/mcp.py $TICKET tools/list | jq -r ".tools[] | \"\(.name) \(.inputSchema.properties | keys)\""'

section recent
run 'scripts/mcp.py $TICKET recent_payments | jq -r ".content[0].text | fromjson[] | \"\(.id)  \(.merchant)  \(.amountCents)  \(.card)\""'

section other-customer
run 'scripts/mcp.py $TICKET payment_detail "{\"paymentId\":\"PAY-2210\"}" | jq -c "{isError, text: .content[0].text}"'

section policy
run 'scripts/mcp.py $TICKET search_policy "{\"question\":\"Do you refund processing fees?\"}" | jq -r ".content[0].text | fromjson[0] | \"\(.source)  \(.text)\""'

section claude
run 'scripts/ask.sh CUST-17 "I was charged twice for order 1043. Please refund the duplicate." >/dev/null'
run "grep 'TOOL ' $LOG | sed 's/.*TOOL /TOOL /' | cut -c1-86"
printf '$ psql -c "select payment_id, amount_cents, status from refunds"\n'; sql "select payment_id, amount_cents, status from refunds"
printf '$ psql -c "select count(*) from provider_calls"\n'; sql "select count(*) from provider_calls"

section approve
ID=$(sql "select id from refunds limit 1")
run "curl -s -o /dev/null -w 'ticket token: HTTP %{http_code}\n' -X POST localhost:8420/admin/refunds/$ID/approve -H \"Authorization: Bearer \$TICKET\""
run "curl -s -X POST localhost:8420/admin/refunds/$ID/approve -H \"Authorization: Bearer \$LEAD\"; echo"
run "curl -s -X POST localhost:8420/admin/refunds/$ID/approve -H \"Authorization: Bearer \$LEAD\"; echo"
printf '$ psql -c "select count(*) from provider_calls"\n'; sql "select count(*) from provider_calls"
printf '$ psql -c "select event, actor from refund_audit order by id"\n'; sql "select event, actor from refund_audit order by id"

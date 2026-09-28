#!/bin/sh
# Rerun everything in the video on your machine.
#   scripts/verify.sh                the tests, every demonstration, the transport check
#   scripts/verify.sh --experiments  also the model runs (needs Claude Code, signed in)
# Writes its transcripts to runs/verify/. A fresh demo secret is generated every
# run; it is a local test credential, never a production one.
set -e
DIR=$(cd "$(dirname "$0")/.." && pwd); cd "$DIR"
python3 -c "import secrets;print('SUPPORT_JWT_SECRET='+secrets.token_hex(32))" > .env.local
. scripts/java25.sh
OUT=runs/verify; mkdir -p "$OUT"

echo "== boundary tests, no model"
scripts/test-summary.sh | tee "$OUT/tests.txt"
echo "== every demonstration, from a fresh database"
scripts/demo.sh | tee "$OUT/demo.txt"
echo "== which transport did the server start?"
scripts/transport-check.sh | tee "$OUT/transport.txt"
if [ "$1" = "--experiments" ]; then
  echo "== model runs: legitimate request, 24 policy questions, 12 injections"
  LOGS="$OUT/logs" experiments/run-all.sh
fi
pkill -f support-agent-0.0.1 2>/dev/null || true
echo "VERIFIED: transcripts in $OUT"

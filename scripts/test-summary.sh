#!/bin/sh
# Run the no-model boundary tests and print what they proved, from the report.
#   scripts/test-summary.sh
set -e
DIR=$(cd "$(dirname "$0")/.." && pwd); cd "$DIR"
JAVA_HOME=${JAVA_HOME_25:-$HOME/.sdkman/candidates/java/25.0.4-amzn}; export JAVA_HOME
printf '$ ./mvnw test\n'
./mvnw -q test >/dev/null 2>&1 || true
python3 - <<'PY'
import glob, re, xml.etree.ElementTree as ET
cases, fail = [], 0
for f in glob.glob("target/surefire-reports/TEST-*.xml"):
    for tc in ET.parse(f).getroot().iter("testcase"):
        bad = any(c.tag in ("failure", "error") for c in tc)
        fail += bad
        words = re.sub(r"(?<!^)(?=[A-Z])", " ", tc.get("name")).lower()
        cases.append(("FAIL " if bad else "ok   ") + words)
for c in sorted(cases):
    print(c)
print(f"Tests run: {len(cases)}, Failures: {fail}, no model involved")
PY

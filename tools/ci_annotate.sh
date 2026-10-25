#!/usr/bin/env bash
# Emits GitHub Actions annotations from a Gradle log and JUnit XML results.
#
# Usage: tools/ci_annotate.sh <gradle-log> [test-results-dir]
#
# GitHub's log archive is not always reachable from every environment, but
# annotations are exposed through the checks API, so the important failure
# details are surfaced there as well.
set -uo pipefail

LOG="${1:-}"
RESULTS="${2:-app/build/test-results/testDebugUnitTest}"

emit() {
  # GitHub caps annotation messages; keep them readable.
  local message
  message="$(printf '%s' "$1" | tr '\n' ' ' | cut -c1-900)"
  echo "::error title=$2::${message}"
}

if [ -n "${LOG}" ] && [ -f "${LOG}" ]; then
  echo "--- failing Gradle lines ---"
  grep -nE 'e: |error:|FAILURE:|What went wrong|Execution failed|Caused by:|> Task .* FAILED' "${LOG}" \
    | head -n 25 | while IFS= read -r line; do
        echo "${line}"
        emit "${line}" "Gradle"
      done
fi

if [ -d "${RESULTS}" ]; then
  echo "--- failing tests ---"
  python3 - "${RESULTS}" <<'PY'
import glob, sys, xml.etree.ElementTree as ET

root_dir = sys.argv[1]
failed = 0
for path in sorted(glob.glob(root_dir + "/*.xml")):
    try:
        doc = ET.parse(path).getroot()
    except Exception as error:  # pragma: no cover - defensive
        print(f"::error::could not parse {path}: {error}")
        continue
    for case in doc.iter("testcase"):
        for node in list(case.iter("failure")) + list(case.iter("error")):
            failed += 1
            name = f"{case.get('classname')}.{case.get('name')}"
            message = (node.get("message") or "").strip()
            body = (node.text or "").strip()
            detail = (message + " | " + body)[:900].replace("\n", " ")
            print(f"::error title={name}::{detail}")
    tests = doc.get("tests")
    failures = doc.get("failures")
    errors = doc.get("errors")
    print(f"::notice title=Test suite::{path.split('/')[-1]}: {tests} tests, {failures} failures, {errors} errors")
if failed == 0:
    print("::notice title=Failing tests::no failing test records were found in the JUnit XML")
PY
fi

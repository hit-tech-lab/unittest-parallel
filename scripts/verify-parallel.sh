#!/usr/bin/env bash
# Self-verification of the parallel test setup.
#   RUNS=5 scripts/verify-parallel.sh
# 1) default mode (1 JVM, classes + methods concurrent) RUNS times: all green + parallelism measured
# 2) multi-fork mode (2 JVMs for unit, 2 for IT): all green + forks overlapped + isolated DBs
# 3) negative controls: every intentionally broken *ConflictDemo must FAIL with the real conflict
set -euo pipefail
cd "$(dirname "$0")/.."
RUNS="${RUNS:-3}"
MVN="mvn -B -ntp"
LOGS=target/verify-logs
mkdir -p "$LOGS"

run() { # name, mvn args...
  local name=$1; shift
  rm -rf target/parallel-reports target/surefire-reports target/failsafe-reports
  echo "==> $name: mvn $*"
  if ! $MVN "$@" > "$LOGS/$name.log" 2>&1; then
    echo "  [FAIL] maven build failed, see $LOGS/$name.log"; grep -E '\[ERROR\]' "$LOGS/$name.log" | head -20; exit 1
  fi
  grep -E '^\[(INFO|WARNING|ERROR)\] Tests run: [0-9]+, .*Skipped: [0-9]+$' "$LOGS/$name.log" | sed 's/^/  /' || true
}

$MVN -q -DskipTests test-compile > "$LOGS/compile.log" 2>&1

for n in $(seq 1 "$RUNS"); do
  run "single-$n" verify
  python3 scripts/check_reports.py single target
done

run forked verify -Dunit.forks=2 -Dit.forks=2
python3 scripts/check_reports.py forked target

run demo verify -Pconflict-demo
python3 scripts/check_reports.py demo target

echo
echo "ALL CHECKS PASSED: $RUNS x single-JVM run, 1 x multi-fork run, all conflict demos detected."

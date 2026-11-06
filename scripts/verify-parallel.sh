#!/usr/bin/env bash
# Self-verification of v2 (library + demo-app).
#   RUNS=3 scripts/verify-parallel.sh
#  1. library self-tests + demo-app default build (static scan, unit, IT)    x RUNS
#  2. multi-fork run
#  3. conflict demos with the guard OFF  -> the conflicts really happen
#  4. conflict demos with the guard ON   -> each class is rejected first, with the expected rule ids
#  5. runtime-only rules (SQL PT12 via JPA / @Sql / JdbcTemplate, pool PT11) and context budget (PT10)
#  B1 regression: guard disabled -> per-test mocks still routed, ITs still green
set -euo pipefail
cd "$(dirname "$0")/.."
RUNS="${RUNS:-3}"
MVN="mvn -B -ntp"
LOGS=verify-logs
mkdir -p "$LOGS"
T=demo-app/target
CHECK="python3 scripts/check_reports.py"

run() { # name, maven args... ; demo-app is always built from clean (demo profiles add extra test sources)
  local name=$1; shift
  mkdir -p "$LOGS"; echo "==> $name: mvn $*"
  if ! $MVN "$@" > "$LOGS/$name.log" 2>&1; then
    echo "  [FAIL] maven build failed, see $LOGS/$name.log"; grep -E '\[ERROR\]' "$LOGS/$name.log" | head -30; exit 1
  fi
  grep -E '^\[(INFO|WARNING|ERROR)\] Tests run: [0-9]+, .*Skipped: [0-9]+$' "$LOGS/$name.log" | sed 's/^/  /' || true
}

run install clean install          # library (83 self-tests) installed + demo-app full build
$CHECK single $T
for n in $(seq 2 "$RUNS"); do
  run "single-$n" -pl demo-app clean verify
  $CHECK single $T
done

run forked -pl demo-app clean verify -Dunit.forks=2 -Dit.forks=2
$CHECK forked $T

# regression B1: switching the rules off must not change test behaviour (mocks still routed)
run guard-disabled-still-routes -pl demo-app clean verify -DskipUnitTests -Dguard.enabled=false
$CHECK rules $T "TransferApiIT=,TransferServiceIT=,ConcurrentTransferIT="

run conflict-guard-off -pl demo-app clean verify -Pconflict-demo -Dguard.enabled=false
$CHECK conflict $T

run conflict-guard-on -pl demo-app clean verify -Pconflict-demo
$CHECK rules $T "SharedMockitoBeanConflictDemo=PT01+PT03,SharedDatabaseConflictDemo=PT01+PT07,SharedSingletonStateConflictDemo=PT01+PT07,DirtiesContextConflictDemo=PT01+PT04+PT07,TransactionalRollbackConflictDemo=PT01"

run guard-runtime -pl demo-app clean verify -Pguard-demo
$CHECK rules $T "ForbiddenSqlGuardDemo=PT12,SqlScriptGuardDemo=PT12,JdbcTemplateGuardDemo=PT12,PoolTooSmallGuardDemo=PT11"

run guard-budget -pl demo-app clean verify -Pguard-demo "-Dit.includes=**/*BudgetDemo.java" -Dparalleltest.max-contexts=1
$CHECK one-of $T PT10 ContextBudgetABudgetDemo,ContextBudgetBBudgetDemo

echo
echo "ALL CHECKS PASSED: $RUNS default runs, multi-fork run, conflicts reproduced with guard off and rejected with guard on, runtime rules PT10/PT11/PT12 enforced."

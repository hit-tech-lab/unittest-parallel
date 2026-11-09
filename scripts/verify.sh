#!/usr/bin/env bash
# Xác minh toàn bộ nhánh Spring Boot 3 (Spring 6.1, JUnit 5, Mockito 5, Java 17): test đơn vị của thư viện + bộ IT mẫu
# (H2, Postgres database/schema, nhiều fork, chế độ auto) + đối chứng âm. Đọc kết quả thật từ báo cáo và so với số test kỳ vọng.
#   scripts/verify.sh
# Biến môi trường:
#   JAVA_HOME   JDK 17 (bắt buộc đúng 17)
#   MVN         lệnh Maven; mặc định ./mvnw trong repo nếu có, ngược lại `mvn` trong PATH
#   RUNS        số lần chạy lặp chế độ H2 (mặc định 2)
#   SKIP_PG     =1 bỏ qua các chế độ Postgres (không có Docker); script nói rõ là đã bỏ qua
# Các chế độ Postgres cần Docker (Testcontainers); thiếu Docker mà không đặt SKIP_PG=1 thì script dừng, không im lặng bỏ qua.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="$HERE/lib-tests/target"
LOGS="$TARGET/verify-logs"
RUNS="${RUNS:-2}"
FORK_SUBSET='Aop*IT,PostConstructIT,CustomSchemaIT,UnboundThread*IT'

if [ -n "${MVN:-}" ]; then
  :
elif [ -x "$HERE/mvnw" ]; then
  MVN="$HERE/mvnw"
elif command -v mvn >/dev/null 2>&1; then
  MVN="$(command -v mvn)"
else
  echo "Không tìm thấy Maven: đặt biến MVN hoặc đưa mvn vào PATH." >&2
  exit 1
fi

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVA_MAJOR="$("$JAVA_BIN" -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
if [ "$JAVA_MAJOR" != "17" ]; then
  echo "Nhánh này cần JDK 17 nhưng '$JAVA_BIN' là phiên bản '${JAVA_MAJOR:-không xác định}': đặt JAVA_HOME trỏ tới JDK 17." >&2
  exit 1
fi

if [ "${SKIP_PG:-0}" = "1" ]; then
  echo "==> Postgres: BỎ QUA (SKIP_PG=1); các chế độ Postgres CHƯA được xác minh" >&2
elif ! docker info >/dev/null 2>&1; then
  echo "Docker không chạy: cần cho các chế độ Postgres. Bật Docker hoặc đặt SKIP_PG=1." >&2
  exit 1
fi
mkdir -p "$LOGS"

# In dòng tổng kết "Tests run"; không có dòng đó (lỗi biên dịch, thiếu Docker, Maven lỗi...) thì in đuôi log và dừng, không im lặng.
summarize() { # name, log: in dòng tổng kết của từng module (parallel-test-lib rồi lib-tests)
  local lines
  lines="$(grep -E "^\[(INFO|ERROR)\] Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$" "$2" || true)"
  if [ -z "$lines" ]; then
    echo "  [FAIL] $1: không thấy dòng 'Tests run' (build lỗi trước khi test chạy?). 40 dòng cuối của $2:" >&2
    tail -n 40 "$2" >&2
    exit 1
  fi
  echo "$lines" | sed 's/^/  /'
}

run() { # name, checkArgs..., -- mvnArgs...
  local name=$1; shift
  local check=(); while [ "$1" != "--" ]; do check+=("$1"); shift; done; shift
  rm -rf "$TARGET/ptest-reports" "$TARGET/surefire-reports"
  echo "==> $name"
  "$MVN" -B -ntp -f "$HERE/pom.xml" -pl lib-tests -am verify "$@" > "$LOGS/$name.log" 2>&1 || true
  summarize "$name" "$LOGS/$name.log"
  python3 "$HERE/scripts/check_reports.py" "${check[@]}"
}

# Mỗi lần chạy build cả parallel-test-lib (-am) nên in hai dòng: test đơn vị của thư viện, rồi bộ IT/demo của lib-tests.
for n in $(seq 1 "$RUNS"); do run "h2-$n" it "$TARGET" h2 database -- ; done
run auto-h2 auto "$TARGET" -- -Dtest.dbmode.arg= -Dtest.groups=auto-default -Dtest.excludedGroups=
run forks-h2 forks "$TARGET" h2 -- -Dtest.forks=2 "-Dtest=$FORK_SUBSET" -Dsurefire.failIfNoSpecifiedTests=false
run demo-h2 demo "$TARGET" -- -Pconflict-demo -Dptest.max-contexts=20

if [ "${SKIP_PG:-0}" != "1" ]; then
  run pg-database it "$TARGET" postgres database -- -Dptest.db.mode=postgres -Dptest.db.isolation=database
  # max_connections khác mặc định (300): DbPoolConfigIT kiểm SHOW max_connections đúng 500 trên server thật
  run pg-schema it "$TARGET" postgres schema -- -Dptest.db.mode=postgres -Dptest.db.isolation=schema -Dptest.db.postgres.max-connections=500
  run forks-pg forks "$TARGET" postgres -- -Dtest.forks=2 "-Dtest=$FORK_SUBSET" -Dsurefire.failIfNoSpecifiedTests=false -Dptest.db.mode=postgres
fi
echo "ALL CHECKS PASSED"

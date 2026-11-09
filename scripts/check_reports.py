#!/usr/bin/env python3
"""Kiểm tra bằng chứng của một lần chạy (nhánh Spring Boot 3.3 / Java 17). Exit code != 0 nếu có kiểm tra thất bại.

  check_reports.py it    <target> <h2|postgres> <database|schema>   # bộ IT: xanh, song song thật, số context, DB riêng
  check_reports.py forks <target> <h2|postgres>                     # nhiều fork: JVM riêng, DB không trùng
  check_reports.py auto  <target>                                   # chế độ mặc định (không đặt ptest.db.mode): auto -> H2
  check_reports.py demo  <target>                                   # đối chứng âm: mỗi demo PHẢI fail đúng lý do (kể cả guard PT03..PT13)

Mỗi lần chạy bị ràng buộc bằng SỐ TEST CHÍNH XÁC và không có skip nào ngoài DECLARED_SKIPS; mode không biết => lỗi (exit 2).
"""
import glob, os, re, sys
import xml.etree.ElementTree as ET

failures = []
# tên context -> số context dự kiến (cùng tên "label" nhưng khác cấu hình Spring => 2 context)
EXPECTED_CONTEXTS = {"main": 1, "alt": 1, "db-a": 1, "db-b": 1, "audit": 1, "policy-real": 1, "policy-fail": 1,
                     "policy-guarded": 1, "user-url": 1, "svc-conn": 1, "details-bean": 1, "dyn-prop": 1, "label": 2, "cleanup-fail": 1, "cleanup-ok": 1, "cleanup-probe": 1, "cleanup-rollback": 1, "project": 1, "project-extra": 1, "replace": 1, "mockbean-props-getter-new": 1, "mockbean-props-getter-replaced": 1, "mockbean-props-setter": 1, "mockbean-skip-pp-new": 1, "mockbean-skip-pp-replaced": 1,
                     # đối tượng của các IT cấu hình/ma trận mới: db-pool*/pg-*: pool + max_connections + WARN; nested-ctx/tiers/sets/web-mock: khóa context
                     "db-pool": 1, "db-pool-user": 1, "pg-warn": 1, "pg-nowarn": 1, "nested-ctx": 2, "tiers": 2, "sets": 3, "web-mock": 1,
                     "a-context-name-much-longer-than-sixty-three-characters-so-that-postgres-would-truncate-the-identifier": 1}
# Context chỉ tồn tại ở chế độ postgres (PostgresWarnIT bị bỏ qua ở H2 nên không tạo context)
POSTGRES_ONLY_CONTEXTS = {"pg-warn", "pg-nowarn"}


def expected_contexts(mode):
    return {k: v for k, v in EXPECTED_CONTEXTS.items() if mode == "postgres" or k not in POSTGRES_ONLY_CONTEXTS}


# context lỗi lúc cấp DB (rollback) không bao giờ có DB; mỗi context còn lại dùng 1 DB
# (user-url / svc-conn / dyn-prop: người dùng sở hữu DataSource nên thư viện KHÔNG cấp DB; project: không có DataSource nên không ai hỏi URL)
NO_DB_CONTEXTS = {"cleanup-rollback", "user-url", "svc-conn", "details-bean", "dyn-prop", "project", "project-extra", "replace", "mockbean-props-getter-new", "mockbean-props-getter-replaced", "mockbean-props-setter", "mockbean-skip-pp-new", "mockbean-skip-pp-replaced", "pg-warn", "pg-nowarn"}
# Số test mong đợi CHÍNH XÁC (đếm từ @Test/@RepeatedTest/@ParameterizedTest); đôi khi thêm/bớt test và phải sửa ở đây.
EXPECTED_TESTS = {"it": 145, "auto": 1, "forks": 19, "demo": 33}
# Skip được khai báo trước (lớp -> số skip) theo chế độ: ContextCleanupIT bỏ qua kiểm tra pg_database và DbPoolConfigIT bỏ qua SHOW max_connections, PostgresWarnIT (2 test) bỏ qua WARN của provisioner Postgres trên H2 (assumeTrue).
DECLARED_SKIPS = {"h2": {"ContextCleanupIT": 1, "DbPoolConfigIT": 1, "PostgresWarnIT": 2}, "postgres": {}}
MAX_CONTEXTS = int(os.environ.get("MAX_CONTEXTS", "40"))


def check(cond, msg):
    print(("  [OK]   " if cond else "  [FAIL] ") + msg)
    if not cond:
        failures.append(msg)


def load(target):
    out = []
    for path in sorted(glob.glob(os.path.join(target, "ptest-reports", "fork*.properties"))):
        props = {}
        for line in open(path):
            if line.startswith("#") or "=" not in line:
                continue
            k, v = line.rstrip("\n").split("=", 1)
            props[k] = v.replace("\\:", ":").replace("\\=", "=").replace("\\#", "#")
        out.append(props)
    return out


def i(r, k):
    return int(r.get(k, "0") or 0)


def suites(target):
    return [ET.parse(p).getroot() for p in glob.glob(os.path.join(target, "surefire-reports", "TEST-*.xml"))]


def count_tests(target, scenario, mode=None):
    ss = suites(target)
    tests = sum(int(s.get("tests")) for s in ss)
    bad = sum(int(s.get("failures")) + int(s.get("errors")) for s in ss)
    skipped = {s.get("name").rsplit(".", 1)[-1]: int(s.get("skipped")) for s in ss if int(s.get("skipped"))}
    check(tests == EXPECTED_TESTS[scenario], f"{tests} tests ran, exactly {EXPECTED_TESTS[scenario]} expected")
    declared = DECLARED_SKIPS.get(mode, {}) if scenario == "it" else {}
    check(skipped == declared, f"skipped tests {skipped} == declared skips {declared}")
    return tests, bad


def exit_state(r, label=""):
    """Trạng thái lúc JVM thoát (ghi bởi shutdown hook sau khi Spring đóng các context)."""
    check("exitContextsActive" in r, f"{label}exit snapshot present (shutdown hook ran)")
    check(i(r, "exitContextsActive") == 0, f"{label}no active context at exit ({r.get('exitContextsActive')})")
    check(i(r, "exitLeftoverDbs") == 0 and "exitContextsActive" in r,
          f"{label}no DB left behind at exit ({r.get('exitLeftoverDbs')}: {r.get('exitLeftoverDbUrls', '')})")


def db_ok(r, mode, isolation):
    urls = [d.split(" -> ", 1)[1] for d in r.get("databases", "").split(" | ") if " -> " in d]
    expected_dbs = i(r, "contextsCreated") - sum(1 for c in r["contexts"].split() if c.split("@")[0] in NO_DB_CONTEXTS)
    check(len(urls) == expected_dbs, f"every context has its own DB resource ({len(urls)} DBs / {r.get('contextsCreated')} contexts, {expected_dbs} expected)")
    check(len(set(urls)) == len(urls), "all DB urls distinct")
    if mode == "h2":
        check(all(u.startswith("jdbc:h2:mem:") for u in urls), "mode h2: in-memory urls")
    else:
        check(all(u.startswith("jdbc:postgresql://") for u in urls), "mode postgres: urls point at the Testcontainers server")
        schema_urls = [u for u in urls if "currentSchema=" in u]
        if isolation == "schema":
            # context 'audit' khai báo schema tùy chỉnh => tự động dùng database riêng (cleanup-rollback cũng vậy nhưng không có URL)
            check(len(schema_urls) == len(urls) - 1, f"isolation=schema: {len(schema_urls)} schema-isolated + 1 database-isolated (audit, custom schema)")
        else:
            check(len(schema_urls) == 0, "isolation=database: no schema-isolated context")


def it(target, mode, isolation):
    reps = load(target)
    check(len(reps) == 1, f"exactly 1 JVM report (got {len(reps)})")
    ss = suites(target)
    tests, bad = count_tests(target, "it", mode)
    check(bad == 0, f"{tests} tests, {bad} failed")
    for r in reps:
        check(r["dbMode"] == mode, f"db mode = {r['dbMode']}")
        check(r["dbModeRequested"] == mode, f"requested mode = {r['dbModeRequested']}")
        exit_state(r)
        check(i(r, "maxConcurrentTests") >= 8, f"test methods overlapped (max concurrent = {r['maxConcurrentTests']})")
        check(i(r, "maxConcurrentClasses") >= 8, f"test classes overlapped (max concurrent = {r['maxConcurrentClasses']})")
        from collections import Counter
        counts = Counter(c.split("@")[0].replace("\\", "") for c in r["contexts"].split())
        expected = expected_contexts(mode)
        check(dict(counts) == expected, f"contexts created = {dict(sorted(counts.items()))} ({r['contextsCreated']} total)")
        check(i(r, "contextsCreated") == sum(expected.values()), "each profile/configuration created exactly once (cache reuse across the classes)")
        check(i(r, "contextsPeakActive") <= MAX_CONTEXTS, f"peak active contexts {r['contextsPeakActive']} <= max {MAX_CONTEXTS}")
        check(i(r, "interceptedCalls") > 0, f"calls routed to per-test mock/spy: {r['interceptedCalls']}, real: {r['realCalls']}, unbound: {r['unboundCalls']}")
        db_ok(r, mode, isolation)
        guard_clean(r)
    check_parallel_safety_it(target)
    check_project_suite(target)


def check_project_suite(target):
    """Nhiều lớp với mock/spy/real khác nhau cho cùng bean (demo.project, trừ ProjectExtraMockOnlyIT: khai thêm mock-only) phải ra ĐÚNG 1 context 'project'."""
    classes = sorted(s.get("name").rsplit(".", 1)[-1] for s in suites(target)
                     if s.get("name").startswith("demo.project.Project") and not s.get("name").endswith("ProjectExtraMockOnlyIT"))
    check(len(classes) == 11, f"{len(classes)} classes of the project suite executed (real/spy/mock of the same beans): {classes}")
    for r in load(target):
        n = sum(1 for c in r["contexts"].split() if c.split("@")[0] == "project")
        check(n == 1, f"{len(classes)} classes with different mocks/spies share exactly {n} context 'project' (expected 1)")


def guard_clean(r):
    """Guard THẬT SỰ đã chạy (bộ đếm > 0) và không có vi phạm/cảnh báo nào trong lần chạy sạch."""
    check(i(r, "guardSqlChecked") > 0, f"guard JDBC proxy active: {r.get('guardSqlChecked')} SQL statements checked")
    check(i(r, "guardPoolsChecked") > 0, f"pool size (PT11) checked for {r.get('guardPoolsChecked')} (class, context) pairs")
    check(i(r, "guardClassesChecked") >= 70, f"static rules checked {r.get('guardClassesChecked')} classes (extension + ParallelSafetyIT)")
    check(i(r, "guardViolationsFailed") == 0, f"no guard violation in a clean run ({r.get('guardViolationsFailed')})")
    check(i(r, "guardViolationsWarned") == 0, f"no guard warning in a clean run ({r.get('guardViolationsWarned')})")
    check(i(r, "guardForeignThreadViolations") == 0, "no violation attributed to another thread in a clean run")


def check_parallel_safety_it(target):
    """Quét tĩnh cả source set test (trừ package demo.demo) PHẢI chạy và xanh trong bộ IT."""
    found = [s for s in suites(target) if s.get("name") == "demo.arch.ParallelSafetyIT"]
    check(len(found) == 1 and int(found[0].get("tests")) == 1, "ParallelSafetyIT (static scan of the whole test source set) executed once")
    check(bool(found) and int(found[0].get("failures")) + int(found[0].get("errors")) == 0, "ParallelSafetyIT passed: no PT03/PT06/PT07/PT08/PT13 violation left in the consumer tests")


def auto(target):
    """Không đặt ptest.db.mode: auto => H2 (H2 có trên classpath), đường Boot."""
    reps = load(target)
    check(len(reps) == 1, f"exactly 1 JVM report (got {len(reps)})")
    tests, bad = count_tests(target, "auto")
    check(bad == 0, f"{tests} tests, {bad} failed")
    for r in reps:
        check(r["dbModeRequested"] == "auto", f"requested mode = {r['dbModeRequested']} (unset => auto)")
        check(r["dbMode"] == "h2", f"auto resolved to {r['dbMode']}")
        check(i(r, "contextsCreated") == 1 and r["contexts"].startswith("auto-default@"), f"contexts = {r['contexts']}")
        db_ok(r, "h2", "database")
        exit_state(r)


def forks(target, mode):
    reps = load(target)
    check(len(reps) >= 2, f"{len(reps)} JVM forks reported")
    check(len({r["pid"] for r in reps}) == len(reps), "forks are distinct processes")
    ss = suites(target)
    tests, bad = count_tests(target, "forks")
    check(bad == 0, f"{tests} tests, {bad} failed")
    for r in reps:
        exit_state(r, f"fork {r['fork']}: ")
    latest_start = max(i(r, "startMillis") for r in reps)
    earliest_end = min(i(r, "endMillis") for r in reps)
    check(latest_start < earliest_end, f"forks ran at the same time (overlap {earliest_end - latest_start} ms)")
    urls = []
    for r in reps:
        us = [d.split(" -> ", 1)[1] for d in r.get("databases", "").split(" | ") if " -> " in d]
        check(all(f"_f{r['fork']}_" in u for u in us if mode == "h2" or "currentSchema" not in u),
              f"fork {r['fork']}: DB names tagged with own fork number ({len(us)} DBs)")
        urls += us
    if mode == "h2":
        check(len(urls) == len(set(urls)), f"{len(urls)} H2 urls across forks, all distinct")
    else:
        hostports = {re.match(r"jdbc:postgresql://([^/]+)/", u).group(1) for u in urls}
        check(len(hostports) == len(reps), f"each fork JVM started its own singleton container ({sorted(hostports)})")
        check(len(urls) == len(set(urls)), f"{len(urls)} Postgres DB urls across forks, all distinct")


# demo -> (hậu tố lớp, regex thông điệp ĐẶC THÙ, tên lớp lỗi mong đợi (xuất hiện trong nguyên nhân), kiểm tra thêm)
# kiểm tra thêm cho một demo
def _budget_diff(texts):
    """PT10: thông điệp có lớp yêu cầu, danh sách lớp tạo context đang chạy, context gần nhất và diff cấu hình (existing/new)."""
    return all(re.search(p, texts, re.S) for p in (
        r"new context was requested by demo\.demo\.ContextLimitConflictDemo",
        r"active contexts were created by: \[demo\.demo\.\w+",
        r"closest active context is '[^']+' \(created by demo\.demo\.\w+\); the new one differs in:",
        r"\* context customizers [^\n]*:\s+existing: \[[^\n]*\]\s+new:\s+\[[^\n]*ptest\[context=over-limit,",
        r"\[PT10\] fix: make the class reuse an existing configuration"))


DEMOS = {
    "SharedStateConflict": ("A,B", r"expected my own AuditLog, but it has the other test's event", "AssertionError", None),
    "SharedDbConflict": ("A,B", r"Table \"ACCOUNT\" not found \(this database is empty\); SQL statement:\s+select \w+\.id,[^\]]* from account ", "InvalidDataAccessResourceUsageException", None),
    "ContextLimitConflict": ("", r"CONTEXT_LIMIT_EXCEEDED: cannot create context .*ptest.max-contexts=1\)", "ParallelTestException\\$ContextLimit", "_budget_diff"),
    "NameConflict": ("A,B", r"CONTEXT_NAME_CONFLICT: context name 'dup' is declared with different wrap/exclude/schemas by "
                            r"demo\.demo\.NameConflict[AB]Demo .* and demo\.demo\.NameConflict[AB]Demo", "ParallelTestException\\$Config", None),
    "NotWrappedConflict": ("", r"BEAN_NOT_WRAPPED: bean 'auditLog' of context 'narrow' has no ptest wrapper \(excluded by @ParallelSpringTest\(exclude=\[auditLog\]\)\)", "ParallelTestException\\$Config", None),
    "DeclModeConflict": ("", r"MODE_CONFLICT: bean 'pricingService' is both mocked and spied in the same test hierarchy", "ParallelTestException\\$Config", None),
    "DeclModeConflictHierarchy": ("", r"MODE_CONFLICT: bean 'pricingService' is both mocked and spied .*DeclHierarchyBase\.baseMock.*DeclModeConflictHierarchyDemo\.childSpy", "ParallelTestException\\$Config", None),
    "DeclAmbiguous": ("", r"BEAN_AMBIGUOUS: 3 beans of type .*PaymentGateway in context 'project': \[gatewayA, gatewayB, gatewayC\]", "ParallelTestException\\$Config", None),
    "PerClassConcurrent": ("", r"CONFIG: @PerTestMock/@PerTestSpy field .* uses TestInstance\.Lifecycle\.PER_CLASS while its methods may run concurrently", "ParallelTestException\\$Config", None),
    "DeclSpyOfMockOnly": ("", r"SPY_NO_BEAN: bean 'mailer' only exists as a mock-only bean: a class-level @PerTestMock CREATED it", "ParallelTestException\\$Config", None),
    "DeclSpyNoBean": ("", r"SPY_NO_BEAN: no bean of type .*AuditLog in context 'project'", "ParallelTestException\\$Config", None),
    "DeclMockNotFound": ("", r"BEAN_NOT_FOUND: no bean of type .*AuditLog in context 'project'.*class level", "ParallelTestException\\$Config", None),
    "DeclInvalidDeclaration": ("", r"INVALID_DECLARATION: @PerTestMock on class .*DeclInvalidDeclarationDemo names no type", "ParallelTestException\\$Config", None),
    "DeclMockReplaceUnsupported": ("", r"MOCK_REPLACE_UNSUPPORTED: bean 'manualMailer' of type .*Ports\$Mailer has no bean definition", "ParallelTestException\\$Config", None),
    "DeclMockOnlyConflict": ("", r"MOCK_ONLY_CONFLICT: bean 'dup' is declared with two types", "ParallelTestException\\$Config", None),
    "DirtiesContextUnsupported": ("", r"DIRTIES_CONTEXT_UNSUPPORTED: @DirtiesContext on demo\.demo\.DirtiesContextUnsupportedDemo", "ParallelTestException\\$Unsupported", None),
    "TransactionalRandomPortUnsupported": ("", r"TRANSACTIONAL_RANDOM_PORT_UNSUPPORTED: @Transactional on demo\.demo\.TransactionalRandomPortUnsupportedDemo", "ParallelTestException\\$Unsupported", None),
    "UnboundThreadConflict": ("", r"UNBOUND_THREAD: thread '[^']+' called \w+\.rate on intercepted bean 'primaryFx' without a test binding", "ParallelTestException\\$UnboundThread", None),
    "MissingSchemaConflict": ("", r"Schema \"AUDIT\" not found; SQL statement:\s+insert into audit\.audit_record ", "InvalidDataAccessResourceUsageException", None),
}


def demo(target):
    count_tests(target, "demo")
    by_class = {}
    for s in suites(target):
        name = s.get("name").rsplit(".", 1)[-1]
        broken = int(s.get("failures")) + int(s.get("errors"))
        text = " ".join((e.get("type") or "") + " " + (e.get("message") or "") + (e.text or "") for e in s.iter() if e.tag in ("failure", "error"))
        by_class[name] = (broken, text)
    for base, (suffixes, pattern, exc_type, extra) in DEMOS.items():
        names = [base + x + "Demo" for x in suffixes.split(",")] if suffixes else [base + "Demo"]
        present = [n for n in names if n in by_class]
        check(len(present) == len(names), f"{base}: {len(present)}/{len(names)} demo classes executed")
        broken = [n for n in present if by_class[n][0] >= 1]
        check(len(broken) >= 1, f"{base}: conflict detected ({len(broken)} failing class(es): {broken})")
        texts = " ".join(by_class[n][1] for n in broken)
        check(re.search(pattern, texts) is not None, f"{base}: failure message matches /{pattern}/")
        if exc_type:
            check(re.search(exc_type, texts) is not None, f"{base}: failure type matches /{exc_type}/")
        if extra:
            fn = globals()[extra] if isinstance(extra, str) else extra
            check(fn(texts), f"{base}: expected details verified ({fn.__name__})")
        check("TESTS_DID_NOT_RUN_CONCURRENTLY" not in texts, f"{base}: not a scheduling problem")
    guard_demos(target)


# Guard demo -> các regex PHẢI khớp (thông điệp chỉ đúng vị trí + mã luật) và regex KHÔNG được khớp (control hợp lệ)
_R = r"\s+what: "
GUARD_CLASS_DEMOS = {
    "GuardForbiddenCallDemo": (
        [r"is not parallel-safe \(5 violations\)"]
        + [rf"\[PT07\] [^\n]*\s+at:   demo\.demo\.GuardForbiddenCallDemo\.wholeTableCalls\(GuardForbiddenCallDemo\.java:\d+\){_R}calls {call} \(forbidden: "
           for call in (r"demo\.core\.AccountRepository\.deleteAll", r"demo\.core\.AccountRepository\.count", r"demo\.core\.AccountRepository\.findAll",
                        r"demo\.core\.AccountRepository\.deleteAllInBatch", r"org\.springframework\.test\.jdbc\.JdbcTestUtils\.deleteFromTables")],
        [r"calls [^\n]*(deleteById|findById)"]),
    "GuardStaticFieldDemo": ([r"\[PT06\] [^\n]*\s+at:   demo\.demo\.GuardStaticFieldDemo\.sharedCounter" + _R + r"static int sharedCounter is not final",
                              r"\(1 violation\)"], [r"IMMUTABLE_CONTROL"]),
    "GuardPerClassDemo": ([r"\[PT13\] [^\n]*\s+at:   demo\.demo\.GuardPerClassDemo\s+what: [^\n]*mutable instance fields: \[GuardPerClassDemo\.lastSeen\]"], []),
    "GuardDefinedPortDemo": ([r"\[PT08\] [^\n]*\s+at:   demo\.demo\.GuardDefinedPortDemo" + _R + r"@SpringBootTest\(webEnvironment = DEFINED_PORT\)"], []),
    "GuardPoolSmallDemo": ([r"\[PT11\] [^\n]*\s+at:   DataSource bean 'dataSource' \(context of demo\.demo\.GuardPoolSmallDemo\)" + _R
                            + r"maximum pool size 2 < JUnit parallelism 24"], []),
}
# GuardSqlDemo: tên test -> (phải fail?, regex phải khớp trong thông điệp)
GUARD_SQL_CASES = {
    "wholeTableDeleteOnTheTestThread": (True, [
        r"\[PT12\] [^\n]*\s+at:   test GuardSqlDemo\.wholeTableDeleteOnTheTestThread, thread '[^']+', called from demo\.demo\.GuardSqlDemo\.wholeTableDeleteOnTheTestThread\(GuardSqlDemo\.java:\d+\)"
        + _R + r"DELETE without WHERE: delete from account"]),
    "wholeTableDeleteOnAnotherThreadIsSwallowedButStillFailsThisTest": (True, [
        r"Violations raised for this test on other threads",
        r"\[PT12\] [^\n]*\s+at:   test GuardSqlDemo\.wholeTableDeleteOnAnotherThreadIsSwallowedButStillFailsThisTest, thread 'pool-\d+-thread-\d+'(, called from [^\n]*)?"
        + _R + r"TRUNCATE: truncate table account"]),
    "controlTargetedDeleteOnTheTestThread": (False, []),
    "controlTargetedStatementsOnAnotherThread": (False, []),
}
# Tổng số vi phạm FAIL guard đếm được trong lần chạy demo: 8 (lớp tĩnh; không có PT03 vì @MockitoBean cần Spring 6.2+) + 1 (PT11) + 2 (PT12: thread test + thread khác)
# + 8 (ParallelSafetyScanDemo liệt kê lại các vi phạm tĩnh của các lớp Guard*Demo) = 19
GUARD_DEMO_FAILED_TOTAL = 19


def case_results(target):
    out = {}
    for s in suites(target):
        for tc in s.iter("testcase"):
            bad = [e for e in tc if e.tag in ("failure", "error")]
            text = " ".join((e.get("message") or e.text or "") for e in bad)  # message only: e.text repeats it plus the stack trace
            out.setdefault(s.get("name").rsplit(".", 1)[-1], {})[tc.get("name") or ""] = (bool(bad), text)
    return out


def guard_demos(target):
    res = case_results(target)
    for cls, (must, must_not) in GUARD_CLASS_DEMOS.items():
        cases = res.get(cls, {})
        text = " ".join(t for _, t in cases.values())
        check(len(cases) == 1 and all(f for f, _ in cases.values()), f"{cls}: failed before any test ran (1 failing container, {len(cases)} reported)")
        for pat in must:
            check(re.search(pat, text) is not None, f"{cls}: message matches /{pat[:90]}/")
        for pat in must_not:
            check(re.search(pat, text) is None, f"{cls}: control not reported /{pat}/")
    sql = res.get("GuardSqlDemo", {})
    check(set(sql) == set(GUARD_SQL_CASES), f"GuardSqlDemo: all {len(GUARD_SQL_CASES)} cases ran ({sorted(sql)})")
    for name, (should_fail, pats) in GUARD_SQL_CASES.items():
        failed, text = sql.get(name, (None, ""))
        check(failed == should_fail, f"GuardSqlDemo.{name}: {'fails' if should_fail else 'passes'} (failed={failed})")
        for pat in pats:
            check(re.search(pat, text) is not None, f"GuardSqlDemo.{name}: message matches /{pat[:90]}/")
    scan = res.get("ParallelSafetyScanDemo", {}).get("testCodeIsParallelSafe", (False, ""))[1]
    check("Test code base is not parallel-safe (8 violations)" in scan, "ParallelSafetyScanDemo: static scan of the source set lists exactly 8 violations")
    codes = sorted(re.findall(r"^\[(PT\d+)\]", scan, re.M))
    check(codes == ["PT06", "PT07", "PT07", "PT07", "PT07", "PT07", "PT08", "PT13"], f"ParallelSafetyScanDemo: rule codes {codes}")
    for cls in GUARD_CLASS_DEMOS:
        if cls not in ("GuardPoolSmallDemo",):
            check(f"demo.demo.{cls}" in scan, f"ParallelSafetyScanDemo: lists {cls}")
    for exempt in ("SharedDbConflictBDemo", "ClosedBindingIT", "UserUrlOnlyIT", "DbIsolationBIT", "GuardSqlDemo"):
        check(exempt not in scan, f"ParallelSafetyScanDemo: does not report {exempt} (suppressed with a reason, or nothing static to report)")
    for r in load(target):
        check(i(r, "guardForeignThreadViolations") == 1, f"exactly 1 violation attributed to another thread ({r.get('guardForeignThreadViolations')})")
        check(i(r, "guardViolationsFailed") == GUARD_DEMO_FAILED_TOTAL,
              f"guard FAIL violations counted = {r.get('guardViolationsFailed')} (expected {GUARD_DEMO_FAILED_TOTAL})")
        check(i(r, "guardSqlChecked") > 0, f"guard JDBC proxy active in the demo run ({r.get('guardSqlChecked')} SQL checked)")


COMMANDS = {"it": (it, 2, "<target> <h2|postgres> <database|schema>"), "forks": (forks, 1, "<target> <h2|postgres>"),
            "auto": (auto, 0, "<target>"), "demo": (demo, 0, "<target>")}
MODES = {"h2", "postgres"}
ISOLATIONS = {"database", "schema"}

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd not in COMMANDS or len(sys.argv) != 3 + COMMANDS[cmd][1]:
        usage = "; ".join(f"{k} {v[2]}" for k, v in COMMANDS.items())
        print(f"check_reports.py: unknown or malformed command {sys.argv[1:]!r}. Usage: {usage}", file=sys.stderr)
        sys.exit(2)
    args = sys.argv[2:]
    if cmd in ("it", "forks") and args[1] not in MODES or cmd == "it" and args[2] not in ISOLATIONS:
        print(f"check_reports.py: unknown mode/isolation in {args!r}", file=sys.stderr)
        sys.exit(2)
    COMMANDS[cmd][0](*args)
    sys.exit(1 if failures else 0)

#!/usr/bin/env python3
"""Checks the evidence produced by a test run. Exit code != 0 if any check fails.

  check_reports.py single  <target>   # forks=1: classes+methods concurrent in one JVM
  check_reports.py forked  <target>   # forks>1: several JVMs really overlapping, isolated DBs
  check_reports.py demo    <target>   # negative controls: every conflict demo must have FAILED
"""
import glob, os, sys
import xml.etree.ElementTree as ET

MAX_IT_CONTEXTS = int(os.environ.get("MAX_IT_CONTEXTS", "3"))
failures = []


def check(cond, msg):
    print(("  [OK]   " if cond else "  [FAIL] ") + msg)
    if not cond:
        failures.append(msg)


def load_probe(target, phase):
    reports = []
    for path in sorted(glob.glob(os.path.join(target, "parallel-reports", phase + "-fork*.properties"))):
        props = {}
        with open(path) as f:
            for line in f:
                if line.startswith("#") or "=" not in line:
                    continue
                k, v = line.rstrip("\n").split("=", 1)
                props[k] = v.replace("\\:", ":").replace("\\=", "=")
        reports.append(props)
    return reports


def i(r, k):
    return int(r.get(k, "0") or 0)


def single(target):
    for phase in ("unit", "it"):
        reps = load_probe(target, phase)
        check(len(reps) == 1, f"{phase}: exactly 1 JVM fork report (got {len(reps)})")
        for r in reps:
            check(i(r, "failedTests") == 0, f"{phase}: no failed tests ({r.get('failedTests')})")
            check(i(r, "maxConcurrentTests") >= 2, f"{phase}: test methods overlapped (max concurrent = {r.get('maxConcurrentTests')})")
            check(i(r, "maxConcurrentClasses") >= 2, f"{phase}: test classes overlapped (max concurrent = {r.get('maxConcurrentClasses')})")
            check(i(r, "threads") >= 2, f"{phase}: tests ran on {r.get('threads')} threads")
            if phase == "it":
                n = i(r, "springContexts")
                check(1 <= n <= MAX_IT_CONTEXTS, f"it: context cache effective, {n} Spring contexts for all IT classes (max {MAX_IT_CONTEXTS})")
                check(i(r, "distinctDatabases") == i(r, "contextsWithDatabase"),
                      f"it: every context has its own database ({r.get('distinctDatabases')} DBs / {r.get('contextsWithDatabase')} contexts)")


def forked(target):
    for phase in ("unit", "it"):
        reps = load_probe(target, phase)
        check(len(reps) >= 2, f"{phase}: {len(reps)} JVM forks reported")
        check(len({r["pid"] for r in reps}) == len(reps), f"{phase}: forks are distinct processes")
        check(len({r["fork"] for r in reps}) == len(reps), f"{phase}: forks have distinct fork numbers {sorted(r['fork'] for r in reps)}")
        check(all(i(r, "failedTests") == 0 for r in reps), f"{phase}: no failed tests in any fork")
        if len(reps) >= 2:
            latest_start = max(i(r, "startMillis") for r in reps)
            earliest_end = min(i(r, "endMillis") for r in reps)
            check(latest_start < earliest_end, f"{phase}: forks ran at the same time (overlap {earliest_end - latest_start} ms)")
        check(any(i(r, "maxConcurrentTests") >= 2 for r in reps), f"{phase}: methods overlapped inside a fork")
        if phase == "it":
            dbs = [db for r in reps for db in r.get("databases", "").split() if db]
            check(len(dbs) == len(set(dbs)) and len(dbs) >= 2, f"it: {len(dbs)} databases across forks, all distinct")
            check(all(db.startswith("jdbc:h2:mem:fork" + r["fork"] + "-") or "demo" in db
                      for r in reps for db in r.get("databases", "").split()),
                  "it: each fork only used databases tagged with its own fork number")


def demo(target):
    files = glob.glob(os.path.join(target, "failsafe-reports", "TEST-*ConflictDemo.xml"))
    check(len(files) >= 5, f"demo: {len(files)} conflict demo classes executed")
    for path in sorted(files):
        suite = ET.parse(path).getroot()
        name = suite.get("name").rsplit(".", 1)[-1]
        broken = int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        messages = " ".join((e.get("message") or "") + (e.text or "")
                            for e in suite.iter() if e.tag in ("failure", "error"))
        check(broken >= 1, f"demo: {name} -> conflict detected ({broken} failing test(s))")
        check("TESTS_DID_NOT_RUN_CONCURRENTLY" not in messages, f"demo: {name} -> failure is the real conflict, not a scheduling problem")


if __name__ == "__main__":
    mode, target = sys.argv[1], sys.argv[2]
    {"single": single, "forked": forked, "demo": demo}[mode](target)
    sys.exit(1 if failures else 0)

#!/usr/bin/env python3
"""Checks the evidence produced by a demo-app test run. Exit code != 0 if any check fails.

  check_reports.py single   <target>          # 1 JVM: classes+methods concurrent, context budget, DB per context
  check_reports.py forked   <target>          # several JVMs overlapping, isolated DBs
  check_reports.py conflict <target>          # guard OFF: every *ConflictDemo fails because of the real conflict
  check_reports.py rules    <target> <spec>   # guard ON: classes fail with exactly these rule ids
       spec = "ClassA=PT01+PT03,ClassB=PT12"  ("ClassX=" means: must pass)
  check_reports.py one-of   <target> <rule> <ClassA,ClassB>   # exactly one of the classes fails with <rule>
"""
import glob, os, re, sys
import xml.etree.ElementTree as ET

MAX_IT_CONTEXTS = int(os.environ.get("MAX_IT_CONTEXTS", "2"))
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


def suites(target):
    out = {}
    for path in glob.glob(os.path.join(target, "failsafe-reports", "TEST-*.xml")):
        s = ET.parse(path).getroot()
        msgs = " ".join((e.get("message") or "") + (e.text or "") for e in s.iter() if e.tag in ("failure", "error"))
        out[s.get("name").rsplit(".", 1)[-1]] = (int(s.get("failures", 0)) + int(s.get("errors", 0)), msgs)
    return out


def single(target):
    for phase in ("unit", "it"):
        reps = load_probe(target, phase)
        check(len(reps) == 1, f"{phase}: exactly 1 JVM fork report (got {len(reps)})")
        for r in reps:
            check(i(r, "failedTests") == 0, f"{phase}: no failed tests ({r.get('failedTests')})")
            check(i(r, "maxConcurrentTests") >= 2, f"{phase}: test methods overlapped (max concurrent = {r.get('maxConcurrentTests')})")
            check(i(r, "maxConcurrentClasses") >= 2, f"{phase}: test classes overlapped (max concurrent = {r.get('maxConcurrentClasses')})")
            if phase == "it":
                n = i(r, "springContexts")
                check(1 <= n <= MAX_IT_CONTEXTS, f"it: {n} Spring contexts for all IT classes (budget {MAX_IT_CONTEXTS})")
                check(i(r, "distinctDatabases") == i(r, "contextsWithDatabase"),
                      f"it: every context has its own database ({r.get('distinctDatabases')} DBs / {r.get('contextsWithDatabase')} contexts)")


def forked(target):
    for phase in ("unit", "it"):
        reps = load_probe(target, phase)
        check(len(reps) >= 2, f"{phase}: {len(reps)} JVM forks reported")
        check(len({r["pid"] for r in reps}) == len(reps), f"{phase}: forks are distinct processes")
        check(all(i(r, "failedTests") == 0 for r in reps), f"{phase}: no failed tests in any fork")
        if len(reps) >= 2:
            overlap = min(i(r, "endMillis") for r in reps) - max(i(r, "startMillis") for r in reps)
            check(overlap > 0, f"{phase}: forks ran at the same time (overlap {overlap} ms)")
        if phase == "it":
            dbs = [db for r in reps for db in r.get("databases", "").split() if db]
            check(len(dbs) == len(set(dbs)) and len(dbs) >= 2, f"it: {len(dbs)} databases across forks, all distinct")


def conflict(target):
    s = suites(target)
    check(len(s) == 5, f"guard OFF: {len(s)} conflict demo classes executed")
    for name, (broken, msgs) in sorted(s.items()):
        check(broken >= 1 and not re.search(r"\[PT\d\d\]", msgs) and "TESTS_DID_NOT_RUN_CONCURRENTLY" not in msgs,
              f"guard OFF: {name} -> the real conflict happened ({broken} failing test(s))")


def rules(target, spec):
    s = suites(target)
    for entry in spec.split(","):
        name, want = entry.split("=")
        expected = set(filter(None, want.split("+")))
        broken, msgs = s.get(name, (None, ""))
        got = set(re.findall(r"\[(PT\d\d)\]", msgs))
        if not expected:
            check(broken == 0, f"{name} passes")
        else:
            check(broken is not None and broken >= 1 and got == expected,
                  f"{name} rejected by the guard with {sorted(expected)} (got {sorted(got)})")


def one_of(target, rule, names):
    s = suites(target)
    hits = [n for n in names.split(",") if rule in s.get(n, (0, ""))[1]]
    check(len(hits) == 1, f"exactly one of {names} fails with {rule} (got {hits})")


if __name__ == "__main__":
    mode, target = sys.argv[1], sys.argv[2]
    {"single": lambda: single(target), "forked": lambda: forked(target), "conflict": lambda: conflict(target),
     "rules": lambda: rules(target, sys.argv[3]), "one-of": lambda: one_of(target, sys.argv[3], sys.argv[4])}[mode]()
    sys.exit(1 if failures else 0)

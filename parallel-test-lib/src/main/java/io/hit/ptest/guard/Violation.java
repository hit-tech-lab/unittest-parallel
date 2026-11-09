package io.hit.ptest.guard;

/** Một vi phạm, chỉ đúng lớp / thành viên / dòng code chịu trách nhiệm. */
public record Violation(GuardRule rule, String location, String detail) {

    @Override
    public String toString() {
        return "[" + rule.name() + "] " + rule.title() + "\n"
                + "    at:   " + location + "\n"
                + (detail == null || detail.isBlank() ? "" : "    what: " + detail + "\n")
                + "    why:  " + rule.why() + "\n"
                + "    fix:  " + rule.fix();
    }
}

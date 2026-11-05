package com.hittech.paralleltest.guard;

import java.util.List;
import java.util.stream.Collectors;

/** One rule violation, pointing at the exact class / member / line responsible. */
public record Violation(Rule rule, String location, String detail) {

    @Override
    public String toString() {
        return "[" + rule.name() + "] " + rule.title() + "\n"
                + "    at:   " + location + "\n"
                + (detail == null || detail.isBlank() ? "" : "    what: " + detail + "\n")
                + "    why:  " + rule.why() + "\n"
                + "    fix:  " + rule.fix();
    }

    public static String format(String header, List<Violation> violations) {
        return "[parallel-test-guard] " + header + " (" + violations.size() + " violation"
                + (violations.size() == 1 ? "" : "s") + ")\n\n"
                + violations.stream().map(Violation::toString).collect(Collectors.joining("\n\n"));
    }
}

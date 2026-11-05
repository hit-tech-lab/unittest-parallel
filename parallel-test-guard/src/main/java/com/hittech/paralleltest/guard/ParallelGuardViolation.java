package com.hittech.paralleltest.guard;

import java.util.List;

/**
 * Thrown when a rule is broken. An Error (not an Exception) so that it is reported as a test failure
 * and is not swallowed or wrapped by framework code (e.g. Hibernate, transaction templates).
 */
public class ParallelGuardViolation extends AssertionError {

    private final transient List<Violation> violations;

    public ParallelGuardViolation(String header, List<Violation> violations) {
        super(Violation.format(header, violations));
        this.violations = List.copyOf(violations);
    }

    public List<Violation> violations() {
        return violations;
    }
}

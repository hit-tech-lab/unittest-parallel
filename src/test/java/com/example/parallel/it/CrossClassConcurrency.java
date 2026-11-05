package com.example.parallel.it;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class CrossClassConcurrency {

    private CrossClassConcurrency() {
    }

    /**
     * With forkCount > 1, Failsafe hands classes to each fork one at a time, so two classes never run
     * concurrently in the same JVM (and cannot conflict there): a cross-class rendezvous is meaningless.
     */
    static void assumeClassesShareJvm() {
        assumeTrue(Integer.getInteger("test.forks", 1) == 1,
                "forkCount > 1: test classes never overlap inside one JVM");
    }
}

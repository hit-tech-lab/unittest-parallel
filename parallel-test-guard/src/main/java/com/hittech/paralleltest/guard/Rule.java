package com.hittech.paralleltest.guard;

/** Every rule has a stable id (grep-able in CI logs), what goes wrong, and how to fix it. */
public enum Rule {

    PT01("Spring test class does not extend a @SharedContextBase class",
            "it gets its own ApplicationContext (slow, and its config drifts from the others)",
            "extend your project's @SharedContextBase class, or annotate @IsolatedContext(reason = \"...\")"),

    PT02("Context configuration changed below the @SharedContextBase",
            "a different configuration creates (and starts) an additional ApplicationContext",
            "move the configuration to the base class, or annotate the class @IsolatedContext(reason = \"...\")"),

    PT03("@MockitoBean/@MockitoSpyBean in a parallel test",
            "the mock is ONE singleton in the shared context: concurrent tests overwrite each other's stubbing, "
                    + "verify() counts foreign calls, and it is reset after every test method",
            "use @PerTestMock (+ @RoutedMocks on the base), or put the class under JUnit @Isolated / @ResourceLock"),

    PT04("@DirtiesContext in a parallel test",
            "closing the cached context breaks every test using it concurrently",
            "remove it (keep tests independent through their own data), or annotate the class JUnit @Isolated"),

    PT05("@PerTestMock type has no router",
            "the context has no router bean for this type: the test would talk to the real bean",
            "add the type to @RoutedMocks on the @SharedContextBase"),

    PT06("Mutable static field in a Spring test class",
            "static state is shared by all concurrent tests of the JVM",
            "use an instance field (a new instance is created per test method) or make it final and immutable"),

    PT07("Call to a forbidden whole-table / global-state method",
            "it reads or deletes data created by tests running at the same time",
            "create unique data per test (TestData) and assert only on rows you created; "
                    + "if really needed, annotate the class JUnit @Isolated"),

    PT08("webEnvironment = DEFINED_PORT",
            "a fixed port clashes between forks, contexts and other builds on the machine",
            "use WebEnvironment.RANDOM_PORT (inject local.server.port)"),

    PT09("@TestExecutionListeners replaces the default listeners",
            "the parallel-test-guard listener is dropped, so none of its runtime checks run for this class",
            "use mergeMode = MERGE_WITH_DEFAULTS"),

    PT10("Context budget exceeded",
            "too many distinct ApplicationContexts: each one is started (serially) and kept in memory, and "
                    + "above spring.test.context.cache.maxSize (32) contexts are evicted while tests may still use them",
            "make the class reuse an existing configuration (see the diff in the message)"),

    PT11("Connection pool smaller than the number of parallel tests",
            "transactional tests hold a connection for the whole test: threads block and time out",
            "set spring.datasource.hikari.maximum-pool-size >= JUnit parallelism in the test profile"),

    PT12("Whole-table SQL executed during a parallel test",
            "TRUNCATE / DELETE without WHERE (JPA, JdbcTemplate, @Sql scripts or plain JDBC) removes rows of tests "
                    + "running at the same time",
            "delete only rows you created (WHERE id = ...), or annotate the class JUnit @Isolated"),

    PT13("@TestInstance(PER_CLASS) with concurrently running methods",
            "all methods share ONE test instance: fields (including @PerTestMock mocks) are overwritten by "
                    + "methods running at the same time",
            "use the default PER_METHOD lifecycle, or annotate the class @Execution(SAME_THREAD)"),

    PT14("Real bean of a @RoutedMocks type is injected somewhere",
            "beans that inject the implementation by concrete class, @Qualifier or as a collection get the REAL "
                    + "bean, bypassing the per-test mocks without any error",
            "inject the routed interface type in production code, or add the concrete type to @RoutedMocks");

    private final String title;
    private final String why;
    private final String fix;

    Rule(String title, String why, String fix) {
        this.title = title;
        this.why = why;
        this.fix = fix;
    }

    public String title() {
        return title;
    }

    public String why() {
        return why;
    }

    public String fix() {
        return fix;
    }
}

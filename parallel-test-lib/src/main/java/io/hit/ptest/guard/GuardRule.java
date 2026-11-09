package io.hit.ptest.guard;

/**
 * Các luật guard. Mã luật (PT..) ổn định, xuất hiện trong mọi thông điệp để grep trên log CI và để cấu hình
 * {@code ptest.guard.<mã>=fail|warn|off}. Mỗi luật có: dạng vi phạm, vì sao nguy hiểm, cách sửa. Đánh số giữ nguyên
 * theo thư viện guard gốc để đối chiếu; các số bị bỏ (PT02, PT04, PT05, PT09, PT14) không tồn tại ở đây.
 */
public enum GuardRule {

    PT01(Kind.STATIC, Severity.WARN, "Spring test class is not managed by @ParallelSpringTest",
            "it gets a context outside the ptest rules: no per-test mock/spy, no own DB, not counted in ptest.max-contexts",
            "annotate the class with @ParallelSpringTest(context = \"...\") or extend a base class that has it"),

    PT03(Kind.STATIC, Severity.FAIL, "@MockitoBean/@MockitoSpyBean (or @MockBean/@SpyBean) in a parallel test",
            "the mock is ONE singleton of the context: concurrent test methods overwrite each other's stubbing, "
                    + "verify() counts foreign calls, and Spring resets it after every method",
            "replace it with a @PerTestMock/@PerTestSpy field (a private mock/spy per test, no new context), "
                    + "or put the class under JUnit @Isolated / @ResourceLock"),

    PT06(Kind.STATIC, Severity.FAIL, "Mutable static field in a test class",
            "static state is shared by every test of the JVM running at the same time",
            "use an instance field (a new instance per test method), make it final and immutable, "
                    + "or annotate the class with JUnit @Isolated"),

    PT07(Kind.STATIC, Severity.FAIL, "Call to a forbidden whole-table / global-state method",
            "it reads or deletes data created by tests running at the same time (shared repository / table)",
            "create unique data per test and assert only on rows you created (findById, query by your own key); "
                    + "if really needed annotate the class with JUnit @Isolated"),

    PT08(Kind.STATIC, Severity.FAIL, "webEnvironment = DEFINED_PORT",
            "a fixed port clashes between contexts, forks and other builds on the machine",
            "use WebEnvironment.RANDOM_PORT (inject @LocalServerPort)"),

    PT10(Kind.RUNTIME, Severity.FAIL, "Context budget exceeded",
            "too many distinct ApplicationContexts: each is started serially and kept in memory, and above "
                    + "spring.test.context.cache.maxSize contexts are evicted while tests may still use them",
            "make the class reuse an existing configuration (see the diff in the message) or raise ptest.max-contexts after review"),

    PT11(Kind.RUNTIME, Severity.FAIL, "Connection pool smaller than the number of parallel tests",
            "transactional tests hold a connection for the whole test: threads block until the pool timeout",
            "set spring.datasource.hikari.maximum-pool-size (or ptest.db.pool-size) >= JUnit parallelism"),

    PT12(Kind.RUNTIME, Severity.FAIL, "Whole-table or schema-changing SQL executed during a parallel test",
            "DELETE/UPDATE without WHERE, TRUNCATE or DROP (JPA, JdbcTemplate, @Sql or plain JDBC) changes rows or tables "
                    + "of tests running at the same time",
            "touch only rows you created (WHERE id = ...), or annotate the class with JUnit @Isolated"),

    PT13(Kind.STATIC, Severity.FAIL, "@TestInstance(PER_CLASS) with mutable state and concurrently running methods",
            "all methods share ONE test instance: its mutable fields are overwritten by methods running at the same time",
            "use the default PER_METHOD lifecycle, make the fields final/injected only, or annotate the class "
                    + "@Execution(SAME_THREAD)");

    /** Luật kiểm tra được không cần khởi động context (STATIC) hay chỉ lúc chạy (RUNTIME). */
    public enum Kind {
        STATIC, RUNTIME
    }

    private final Kind kind;
    private final Severity defaultSeverity;
    private final String title;
    private final String why;
    private final String fix;

    GuardRule(Kind kind, Severity defaultSeverity, String title, String why, String fix) {
        this.kind = kind;
        this.defaultSeverity = defaultSeverity;
        this.title = title;
        this.why = why;
        this.fix = fix;
    }

    public Kind kind() {
        return kind;
    }

    public Severity defaultSeverity() {
        return defaultSeverity;
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

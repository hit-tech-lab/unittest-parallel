package io.hit.ptest.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hit.ptest.ParallelTestException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.SpringProperties;

/** PT12: nhận diện SQL cả bảng (âm + dương), mức WARN/OFF, lớp @Isolated, gán vi phạm trên thread khác cho đúng test. */
@Isolated
class SqlGuardTest {

    @AfterEach
    void clear() {
        SpringProperties.setProperty("ptest.guard.PT12", null);
        GuardScope.useOwnerResolver(() -> null);
    }

    static Stream<Arguments> forbidden() {
        return Stream.of(
                Arguments.of("delete from account", "DELETE without WHERE"),
                Arguments.of("DELETE FROM account a1_0", "DELETE without WHERE"),
                Arguments.of("delete\nfrom account", "DELETE without WHERE"),
                Arguments.of("-- where\ndelete from account", "DELETE without WHERE"),
                Arguments.of("/* where */ delete from account", "DELETE without WHERE"),
                Arguments.of("delete from a where id = 1; delete from b", "DELETE without WHERE"),
                Arguments.of("update account set balance = 0", "UPDATE without WHERE"),
                Arguments.of("UPDATE account SET note = 'where'", "UPDATE without WHERE"),
                Arguments.of("  truncate table account", "TRUNCATE"),
                Arguments.of("TRUNCATE account", "TRUNCATE"),
                Arguments.of("drop table account", "DROP"),
                Arguments.of("select 1; drop schema audit", "DROP"),
                Arguments.of("with c as (select id from t) delete from account", "DELETE without WHERE"),
                Arguments.of("with d as (delete from account returning *) select * from d", "DELETE without WHERE"),
                Arguments.of("WITH c AS (SELECT 1) UPDATE account SET balance = 0", "UPDATE without WHERE"),
                Arguments.of("with u as (update account set balance = 0 returning id) select id from u", "UPDATE without WHERE"),
                Arguments.of("merge into account a using src s on a.id = s.id when matched then delete", "MERGE ... DELETE without condition"),
                Arguments.of("MERGE INTO a USING b ON a.id = b.id WHEN NOT MATCHED BY SOURCE THEN DELETE", "MERGE ... DELETE without condition"));
    }

    @ParameterizedTest
    @MethodSource("forbidden")
    void wholeTableOrSchemaStatementsAreForbidden(String sql, String reason) {
        assertThat(SqlGuard.forbiddenReason(sql)).isEqualTo(reason);
        assertThatThrownBy(() -> SqlGuard.check(sql)).isInstanceOf(ParallelTestException.Guard.class)
                .hasMessageContaining("[PT12]").hasMessageContaining(reason).hasMessageContaining("at:").hasMessageContaining("fix:")
                .satisfies(e -> assertThat(((ParallelTestException.Guard) e).ruleCodes()).containsExactly("PT12"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete from account where id=?", "DELETE FROM account a WHERE a.owner = ?",
            "select count(*) from account", "update account set balance=? where id=?", "insert into account values (?)",
            "delete from account where /* id */ id = 1", "select * from audit where note = 'truncate '",
            "select * from t where note = 'drop table x'", "insert into log values ('delete from x')",
            "select * from dropped_items", "update_log",
            "with c as (select id from t where x = 1) delete from account where id in (select id from c)",
            "with d as (delete from account where id = 1 returning *) select * from d",
            "with c as (select 1) update account set balance = 0 where id = 1",
            "merge into account a using src s on a.id = s.id when matched and s.gone = true then delete when not matched then insert (id) values (s.id)",
            "merge into account a using src s on a.id = s.id when matched then update set balance = s.balance",
            "select * from account for update", "with c as (select * from t for update) select * from c"})
    void targetedStatementsAreAllowed(String sql) {
        assertThat(SqlGuard.forbiddenReason(sql)).isNull();
        assertThatNoException().isThrownBy(() -> SqlGuard.check(sql));
    }

    @Test
    void warnLogsInsteadOfThrowingAndOffSkipsEvenTheCount() {
        SpringProperties.setProperty("ptest.guard.PT12", "warn");
        long failedBefore = Long.parseLong(GuardStats.snapshot().get("guardViolationsFailed"));
        long warnedBefore = Long.parseLong(GuardStats.snapshot().get("guardViolationsWarned"));
        assertThatNoException().isThrownBy(() -> SqlGuard.check("truncate table t"));
        assertThat(Long.parseLong(GuardStats.snapshot().get("guardViolationsWarned"))).isEqualTo(warnedBefore + 1);
        assertThat(Long.parseLong(GuardStats.snapshot().get("guardViolationsFailed"))).isEqualTo(failedBefore);

        SpringProperties.setProperty("ptest.guard.PT12", "off");
        long checked = Long.parseLong(GuardStats.snapshot().get("guardSqlChecked"));
        assertThatNoException().isThrownBy(() -> SqlGuard.check("truncate table t"));
        assertThat(Long.parseLong(GuardStats.snapshot().get("guardSqlChecked"))).isEqualTo(checked);
    }

    @Test
    void anIsolatedClassRunningAllowsWholeTableStatements() {
        IsolationTracker.started();
        try {
            assertThatNoException().isThrownBy(() -> SqlGuard.check("delete from account"));
        } finally {
            IsolationTracker.finished();
        }
        assertThatThrownBy(() -> SqlGuard.check("delete from account")).isInstanceOf(ParallelTestException.Guard.class);
    }

    @Test
    void violationOnAnotherThreadIsAttributedToTheOwningTestEvenIfSwallowed() throws Exception {
        GuardScope.open("test-1", "SomeIT.someMethod", java.util.Set.of());
        AtomicReference<String> currentOwner = new AtomicReference<>("test-1");
        GuardScope.useOwnerResolver(currentOwner::get);
        try {
            Thread server = new Thread(() -> {
                try {
                    SqlGuard.check("delete from account");
                } catch (RuntimeException swallowed) {
                    // ứng dụng nuốt lỗi (như server trả HTTP 500)
                }
            }, "http-server-thread");
            server.start();
            server.join();
        } finally {
            List<Violation> foreign = GuardScope.close("test-1");
            assertThat(foreign).hasSize(1);
            assertThat(foreign.get(0).rule()).isEqualTo(GuardRule.PT12);
            assertThat(foreign.get(0).location()).contains("test SomeIT.someMethod").contains("thread 'http-server-thread'");
        }
    }

    @Test
    void violationOnTheTestThreadIsThrownButNotRecordedTwice() {
        GuardScope.open("test-2");
        GuardScope.useOwnerResolver(() -> "test-2");
        try {
            assertThatThrownBy(() -> SqlGuard.check("truncate table t")).isInstanceOf(ParallelTestException.Guard.class);
        } finally {
            assertThat(GuardScope.close("test-2")).as("thrown directly, nothing recorded").isEmpty();
        }
    }

    @Test
    void violationOnAThreadOfADifferentTestIsNotAssignedToThisOne() throws Exception {
        GuardScope.open("test-3");
        GuardScope.useOwnerResolver(() -> "some-other-test");
        try {
            Thread t = new Thread(() -> {
                try {
                    SqlGuard.check("truncate table t");
                } catch (RuntimeException expected) {
                    // ném trên thread đó
                }
            });
            t.start();
            t.join();
        } finally {
            assertThat(GuardScope.close("test-3")).isEmpty();
        }
    }

    @Test
    void sqlOfATestThatSuppressedPt12IsAllowedOnItsThreadsButOthersStillFail() throws Exception {
        GuardScope.open("test-4", "DropsOwnTableIT.drop", java.util.Set.of("PT12"));
        GuardScope.useOwnerResolver(() -> "test-4");
        try {
            assertThatNoException().isThrownBy(() -> SqlGuard.check("drop table own_table"));
            GuardScope.useOwnerResolver(() -> "someone-else");
            assertThatThrownBy(() -> SqlGuard.check("drop table own_table")).isInstanceOf(ParallelTestException.Guard.class);
        } finally {
            GuardScope.close("test-4");
        }
    }

    @Test
    void locationNamesTheTestAndTheThread() {
        GuardScope.open("test-5", "NamedIT.deletesAll", java.util.Set.of());
        GuardScope.useOwnerResolver(() -> "test-5");
        try {
            assertThatThrownBy(() -> SqlGuard.check("delete from t")).hasMessageContaining("at:   test NamedIT.deletesAll, thread '"
                    + Thread.currentThread().getName() + "'");
        } finally {
            GuardScope.close("test-5");
        }
    }
}

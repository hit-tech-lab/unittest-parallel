package com.hittech.paralleltest.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {"delete from account", "DELETE FROM account a1_0", "  truncate table account",
            "delete\nfrom account"})
    void wholeTableStatementsAreForbidden(String sql) {
        assertThat(SqlGuard.isForbidden(sql)).isTrue();
        assertThatThrownBy(() -> SqlGuard.check(sql)).isInstanceOf(ParallelGuardViolation.class)
                .hasMessageContaining("[PT12]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete from account where id=?", "DELETE FROM account a WHERE a.owner = ?",
            "select count(*) from account", "update account set balance=? where id=?", "insert into account values (?)"})
    void targetedStatementsAreAllowed(String sql) {
        assertThat(SqlGuard.isForbidden(sql)).isFalse();
        assertThatNoException().isThrownBy(() -> SqlGuard.check(sql));
    }
}

package com.example.parallel.guarddemo;

import com.example.parallel.it.AbstractJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

/**
 * NEGATIVE CONTROL (expected: PT12). The classic cleanup script: executed by Spring's @Sql support through
 * plain JDBC (not Hibernate) -- caught by the JDBC-level SQL guard.
 */
class SqlScriptGuardDemo extends AbstractJpaTest {

    @Test
    @Sql(statements = "delete from account")
    void cleansTheTableBeforeTheTest() {
    }
}
